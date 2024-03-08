package com.analysis.cg.manager;

import com.analysis.cg.model.FileDiffInfo;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JGitManager {

    /**
     * 如果使用ssh协议需要将本地SSH私钥放到这个文件里
     */
    private static final String SSH_PRIVATE_KEY_PATH = "tmp/key/id_rsa";

    private static final TransportConfigCallback SSH_TRANSPORT_CONFIG_CALLBACK = new TransportConfigCallback() {
        private final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch sch = super.createDefaultJSch(fs);
                sch.addIdentity(SSH_PRIVATE_KEY_PATH);
                return sch;
            }
        };

        @Override
        public void configure(Transport transport) {
            if (transport instanceof SshTransport) {
                SshTransport sshTransport = (SshTransport) transport;
                sshTransport.setSshSessionFactory(sshSessionFactory);
            }
        }
    };

    public List<FileDiffInfo> branchJavaFileDiffInfo(Repository repository, String targetBranchRefName, String sourceBranchRefName) {
        List<DiffEntry> diffEntries = this.getBranchDiff(repository, targetBranchRefName, sourceBranchRefName);
        List<FileDiffInfo> diffInfos = new ArrayList<>(diffEntries.size());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (DiffFormatter df = new DiffFormatter(out)) {
                df.setRepository(repository);
                for (DiffEntry entry : diffEntries) {
                    //旧文件不存在，跳过分析
                    if (entry.getOldPath().contains("null")) {
                        continue;
                    }
                    df.format(entry);
                    FileDiffInfo diffInfo = new FileDiffInfo();
                    diffInfo.setOldFilePath(entry.getOldPath());
                    if (!entry.getNewPath().contains("null")) {
                        diffInfo.setNewFilePath(entry.getNewPath());
                    }
                    diffInfos.add(diffInfo);
                }
            }
        } catch (Exception e) {
            log.error("branchJavaFileDiffInfo error, repoPath:{}, targetBranch:{}, sourceBranch:{}",
                    repository.getDirectory().getPath(), targetBranchRefName, sourceBranchRefName, e);
        }
        return diffInfos;
    }


    public Repository getRepository(String gitPath) {
        HashCode hashCode = Hashing.md5().hashBytes(gitPath.getBytes());
        String localFilePath = "tmp/repo/" + hashCode;
        File file = new File(localFilePath);
        if (file.exists()) {
            String gitLocalPath = localFilePath + "/.git";
            return this.getLocalRepo(new File(gitLocalPath));
        } else {
            return this.remoteClone(gitPath, file);
        }
    }

    public List<String> getAllFilePath(Repository repository, String branchRefName) {
        log.info("getAllFilePath, localGitPath:{}, branchRef:{}", repository.getDirectory().getAbsolutePath(), branchRefName);
        try {
            Ref head = repository.exactRef(branchRefName);
            if (null == head) {
                throw new IllegalStateException("no head find");
            }
            List<String> filePath = Lists.newArrayList();
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(head.getObjectId());
                RevTree tree = commit.getTree();
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    while (treeWalk.next()) {
                        String pathString = treeWalk.getPathString();
                        if (StringUtils.isNotBlank(pathString)) {
                            filePath.add(pathString);
                        }
                    }
                }
            }
            return filePath;
        } catch (Exception e) {
            log.error("getAllFilePath error, repoPath:{}, branchRefName:{}", repository.getDirectory().getPath(), branchRefName, e);
            return Collections.emptyList();
        }
    }

    public boolean checkout(Repository repository, String branchName) {
        try (Git git = new Git(repository)) {
            if (this.branchNameExist(git, branchName)) {
                git.checkout().setCreateBranch(false).setName(branchName).call();
            } else {
                git.checkout().setCreateBranch(true).setName(branchName).setStartPoint("origin/" + branchName).call();
            }
            git.pull().setTransportConfigCallback(SSH_TRANSPORT_CONFIG_CALLBACK).call();
            return true;
        } catch (Exception e) {
            log.error("checkout error, path:{}, branchName:{}", repository.getDirectory().getPath(), branchName, e);
            return false;
        }
    }

    private List<DiffEntry> getBranchDiff(Repository repository, String targetBranch, String sourceBranch) {
        try (Git git = new Git(repository)) {
            AbstractTreeIterator oldTreeParser = this.prepareTreeParser(repository, sourceBranch);
            AbstractTreeIterator newTreeParser = this.prepareTreeParser(repository, targetBranch);
            if (null != oldTreeParser && null != newTreeParser) {
                return git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).call();
            }
        } catch (Exception e) {
            log.error("getBranchDiff error, repoPath:{}, targetBranch:{}, sourceBranch:{}",
                    repository.getDirectory().getPath(), targetBranch, sourceBranch, e);
        }
        return Collections.emptyList();
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, String ref) throws IOException {
        Ref head = repository.exactRef(ref);
        if (null == head) {
            return null;
        }

        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(head.getObjectId());
            RevTree tree = walk.parseTree(commit.getTree().getId());
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        }
    }

    private Repository remoteClone(String clonePath, File localPath) {
        if (StringUtils.isBlank(clonePath) || null == localPath) {
            return null;
        }
        log.info("remoteClone, clonePath:{}, localPath:{}", clonePath, localPath.getAbsolutePath());
        try (Git result = Git.cloneRepository()
                .setURI(clonePath)
                .setTransportConfigCallback(SSH_TRANSPORT_CONFIG_CALLBACK)
                .setDirectory(localPath)
                .call()) {
            Repository repository = result.getRepository();
            if (null != repository) {
                this.checkout(repository, "main");
            }
            return repository;
        } catch (Exception e) {
            log.error("cloneRepo error, path:{}", clonePath, e);
            return null;
        }
    }

    private Repository getLocalRepo(File localPath) {
        if (null == localPath) {
            return null;
        }
        log.info("getLocalRepo, path:{}", localPath.getAbsolutePath());
        try {
            Repository repository = new FileRepositoryBuilder()
                    .setGitDir(localPath)
                    .build();
            if (null != repository) {
                this.checkout(repository, "main");
            }
            return repository;
        } catch (IOException e) {
            log.error("getLocalRepo error, path:{}", localPath, e);
            return null;
        }
    }

    private boolean branchNameExist(Git git, String branchName) throws GitAPIException {
        return git.branchList().call().stream().anyMatch(e -> e.getName().contains(branchName));
    }


}

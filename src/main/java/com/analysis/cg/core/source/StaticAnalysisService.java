package com.analysis.cg.core.source;

import com.analysis.cg.manager.JGitManager;
import com.analysis.cg.model.AstEntity;
import com.analysis.cg.model.FileDiffInfo;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Java implementation of CallGraph by source code.
 */
@Slf4j
@Service
public class StaticAnalysisService {

    /**
     * main分支head ref
     */
    private static final String MAIN_HEAD_REF = "refs/heads/main";

    /**
     * head ref前缀
     */
    private static final String HEAD_REF_PREFIX = "refs/heads/";


    @Resource
    private JGitManager jGitManager;


    public Set<String> codeChangeMethods(String gitPath, String diffBranchName) {
        if (StringUtils.isAllBlank(gitPath, diffBranchName)) {
            return Collections.emptySet();
        }
        try (Repository repository = jGitManager.getRepository(gitPath)) {
            if (null == repository || !jGitManager.checkout(repository, diffBranchName)) {
                return Collections.emptySet();
            }
            List<FileDiffInfo> diffInfos = jGitManager.branchJavaFileDiffInfo(repository,
                    HEAD_REF_PREFIX + diffBranchName, MAIN_HEAD_REF);
            if (CollectionUtils.isEmpty(diffInfos)) {
                return Collections.emptySet();
            }
            File rootDir = repository.getDirectory().getParentFile();
            String rootPath = rootDir.getAbsolutePath();
            // old methods
            jGitManager.checkout(repository, "main");
            Map<String, MethodDeclaration> oldMethodDeclarations = Maps.newHashMap();
            diffInfos.stream().map(FileDiffInfo::getOldFilePath)
                    .filter(StringUtils::isNotBlank)
                    .forEach(filePath -> {
                        try {
                            Map<String, MethodDeclaration> allMethodDeclaration = this.getAllMethodDeclaration(rootPath, filePath);
                            if (MapUtils.isNotEmpty(allMethodDeclaration)) {
                                oldMethodDeclarations.putAll(allMethodDeclaration);
                            }
                        } catch (Exception e) {
                            log.error("getAllMethodDeclaration error, filePath:{}", filePath);
                        }
                    });
            if (MapUtils.isEmpty(oldMethodDeclarations)) {
                return Collections.emptySet();
            }

            // new methods
            jGitManager.checkout(repository, diffBranchName);
            Map<String, MethodDeclaration> newMethodDeclarations = Maps.newHashMap();
            diffInfos.stream().map(FileDiffInfo::getNewFilePath)
                    .filter(StringUtils::isNotBlank)
                    .forEach(filePath -> {
                        try {
                            Map<String, MethodDeclaration> allMethodDeclaration = this.getAllMethodDeclaration(rootPath, filePath);
                            if (MapUtils.isNotEmpty(allMethodDeclaration)) {
                                newMethodDeclarations.putAll(allMethodDeclaration);
                            }
                        } catch (Exception e) {
                            log.error("getAllMethodDeclaration error, filePath:{}", filePath);
                        }
                    });
            if (MapUtils.isEmpty(newMethodDeclarations)) {
                return oldMethodDeclarations.keySet();
            }

            // diff
            PrinterConfiguration printerConfiguration = new DefaultPrinterConfiguration()
                    .removeOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS))
                    .removeOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_JAVADOC));
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(printerConfiguration);
            Set<String> changeMethods = Sets.newHashSet();
            oldMethodDeclarations.forEach((s, methodDeclaration) -> {
                MethodDeclaration newMethodDeclaration = newMethodDeclarations.get(s);
                if (null == newMethodDeclaration) {
                    changeMethods.add(s);
                    return;
                }
                String oldMethodBodyStr = printer.print(methodDeclaration.getBody().get());
                String newMethodBodyStr = printer.print(newMethodDeclaration.getBody().get());
                if (!StringUtils.equals(oldMethodBodyStr, newMethodBodyStr)) {
                    changeMethods.add(s);
                }
            });
            return changeMethods;
        } catch (Exception e) {
            log.error("CodeDomainService getBranchFileDiffInfo error, gitPath:{}", gitPath, e);
            return Collections.emptySet();
        }
    }

    private Map<String, MethodDeclaration> getAllMethodDeclaration(String projectRootPath, String path) throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(projectRootPath + "/" + path));
        List<ClassOrInterfaceDeclaration> classDeclarations = cu.findAll(ClassOrInterfaceDeclaration.class);
        if (CollectionUtils.isEmpty(classDeclarations)) {
            return Collections.emptyMap();
        }
        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = classDeclarations.get(0);
        List<MethodDeclaration> methodDeclarations = classOrInterfaceDeclaration.findAll(MethodDeclaration.class);
        Map<String, MethodDeclaration> declarationMap = Maps.newHashMapWithExpectedSize(methodDeclarations.size());
        methodDeclarations.forEach(methodDeclaration -> {
            ResolvedMethodDeclaration resolve = methodDeclaration.resolve();
            declarationMap.put(resolve.getQualifiedSignature(), methodDeclaration);
        });
        return declarationMap;
    }

    public AstEntity methodCallGraph(String gitPath) {
        if (StringUtils.isBlank(gitPath)) {
            return null;
        }
        try (Repository repository = jGitManager.getRepository(gitPath)) {
            if (null == repository) {
                return null;
            }
            // Find all java file path
            File rootDir = repository.getDirectory().getParentFile();
            String rootPath = rootDir.getAbsolutePath();
            List<String> allClassPathList = jGitManager.getAllFilePath(repository, MAIN_HEAD_REF).stream()
                    .filter(e -> e.endsWith(".java")).collect(Collectors.toList());
            List<String> sourceRootPathList = this.findAllSourceRootPath(rootDir);
            // Parse all source
            StaticJavaParser.setConfiguration(this.buildJavaParserConfig(sourceRootPathList));
            AstEntity astEntity = new AstEntity();
            allClassPathList.forEach(path -> {
                try {
                    this.parseInterfaceOrClass(astEntity, rootPath, path);
                } catch (Exception e) {
                    log.error("parseInterfaceOrClass error, path:{}", path);
                }
            });
            return astEntity;
        } catch (Exception e) {
            log.error("methodCallAnalysis error, gitPath:{}", gitPath, e);
            return null;
        }
    }

    private void parseInterfaceOrClass(AstEntity astEntity, String projectRootPath, String path) throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(projectRootPath + "/" + path));
        // 类型声明解析
        List<ClassOrInterfaceDeclaration> classDeclarations = cu.findAll(ClassOrInterfaceDeclaration.class);
        if (CollectionUtils.isEmpty(classDeclarations)) {
            return;
        }
        // 类解析(只解析顶层类定义，其他内部类方法会归属到其下）
        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = classDeclarations.get(0);
        ResolvedReferenceTypeDeclaration resolve = classOrInterfaceDeclaration.resolve();
        AstEntity.InterfaceOrClassDeclareInfo interfaceOrClassDeclareInfo = new AstEntity.InterfaceOrClassDeclareInfo();
        interfaceOrClassDeclareInfo.setClassFileRelativePath(path);
        interfaceOrClassDeclareInfo.setSimpleName(classOrInterfaceDeclaration.getNameAsString());
        interfaceOrClassDeclareInfo.setSignature(resolve.getQualifiedName());
        interfaceOrClassDeclareInfo.setInterface(classOrInterfaceDeclaration.isInterface());
        interfaceOrClassDeclareInfo.setAbstract(classOrInterfaceDeclaration.isAbstract());
        NodeList<ClassOrInterfaceType> implementedTypes = classOrInterfaceDeclaration.getImplementedTypes();
        // 实现接口信息
        if (CollectionUtils.isNotEmpty(implementedTypes)) {
            Set<String> signatures = this.getClassSignatures(implementedTypes);
            interfaceOrClassDeclareInfo.setImplementInterfaceSignatures(signatures);
        }
        // 继承类信息
        NodeList<ClassOrInterfaceType> extendedTypes = classOrInterfaceDeclaration.getExtendedTypes();
        if (CollectionUtils.isNotEmpty(extendedTypes)) {
            Set<String> signatures = this.getClassSignatures(extendedTypes);
            interfaceOrClassDeclareInfo.setExtendClassSignatures(signatures);
        }

        // 声明方法解析
        List<MethodDeclaration> methodDeclarations = classOrInterfaceDeclaration.findAll(MethodDeclaration.class);
        if (CollectionUtils.isNotEmpty(methodDeclarations)) {
            Map<String, AstEntity.MethodDeclareInfo> methodDeclareInfoMap = methodDeclarations.stream()
                    .map(e -> this.parseMethod(e, classOrInterfaceDeclaration.getNameAsString()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(AstEntity.MethodDeclareInfo::getSignature, Function.identity(), (v1, v2) -> v1));
            astEntity.getSignature2MethodDeclareMap().putAll(methodDeclareInfoMap);

            // 项目入口识别（目前仅通过注解识别spring http入口，可以扩展其他的模式）
            NodeList<AnnotationExpr> annotations = classOrInterfaceDeclaration.getAnnotations();
            if (CollectionUtils.isNotEmpty(annotations)
                    && annotations.stream().map(AnnotationExpr::getNameAsString)
                    .anyMatch(e -> "Controller".equals(e) || "RestController".equals(e))) {
                methodDeclareInfoMap.forEach((signature, methodDeclareInfo) -> {
                    boolean isEndpoint = methodDeclareInfo.getAnnotationSimpleNames().stream()
                            .anyMatch(e -> "GetMapping".equals(e) || "PostMapping".equals(e)
                                    || "PutMapping".equals(e) || "DeleteMapping".equals(e) || "RequestMapping".equals(e));
                    if (isEndpoint) {
                        astEntity.getEndPointMethodSignatures().add(signature);
                    }
                });
            }
        }
        astEntity.getSignature2InterfaceOrClassDeclareMap().put(interfaceOrClassDeclareInfo.getSignature(), interfaceOrClassDeclareInfo);
    }

    private AstEntity.MethodDeclareInfo parseMethod(MethodDeclaration methodDeclaration, String classSimpleName) {
        try {
            ResolvedMethodDeclaration methodResolve = methodDeclaration.resolve();
            AstEntity.MethodDeclareInfo methodDeclareInfo = new AstEntity.MethodDeclareInfo();
            methodDeclareInfo.setSimpleName(methodDeclaration.getNameAsString());
            methodDeclareInfo.setSignature(methodResolve.getQualifiedSignature());
            methodDeclareInfo.setClassSimpleName(classSimpleName);
            methodDeclareInfo.setPublic(methodDeclaration.isPublic());
            // 填充方法参数信息
            if (CollectionUtils.isNotEmpty(methodDeclaration.getParameters())) {
                List<String> params = this.getParamSignatures(methodDeclaration);
                methodDeclareInfo.setParamTypeList(params);
            }
            // 填充注解信息
            NodeList<AnnotationExpr> annotations = methodDeclaration.getAnnotations();
            if (CollectionUtils.isNotEmpty(annotations)) {
                Set<String> annotationNames = annotations.stream().map(AnnotationExpr::getNameAsString).collect(Collectors.toSet());
                methodDeclareInfo.setAnnotationSimpleNames(annotationNames);
            }
            // 填充方法调用信息
            List<MethodCallExpr> methodCallExprs = methodDeclaration.getBody()
                    .map(e -> e.findAll(MethodCallExpr.class))
                    .orElse(Collections.emptyList());
            if (CollectionUtils.isNotEmpty(methodCallExprs)) {
                List<String> callMethodSignatures = methodCallExprs.stream().map(methodCallExpr -> {
                            try {
                                ResolvedMethodDeclaration resolve = methodCallExpr.resolve();
                                return resolve.getQualifiedSignature();
                            } catch (Throwable throwable) {
                                log.error("cannot resolve: {}", methodCallExpr.getNameAsString());
                                return null;
                            }
                        }).filter(StringUtils::isNotBlank)
                        .collect(Collectors.toList());
                methodDeclareInfo.setCallMethodSignatures(callMethodSignatures);
            }
            return methodDeclareInfo;
        } catch (Throwable e) {
            log.error("parseMethod error! name:{}", methodDeclaration.getNameAsString());
            return null;
        }
    }

    private List<String> getParamSignatures(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getParameters().stream()
                .map(parameter -> {
                    try {
                        return parameter.resolve().getType().asReferenceType().getQualifiedName();
                    } catch (Exception e) {
                        log.error("parameter resolve error, param:{}", parameter.getNameAsString());
                        return null;
                    }
                }).collect(Collectors.toList());
    }

    private Set<String> getClassSignatures(NodeList<ClassOrInterfaceType> types) {
        return types.stream()
                .map(e -> {
                    try {
                        ResolvedReferenceType resolve = (ResolvedReferenceType) e.resolve();
                        return resolve.getQualifiedName();
                    } catch (Throwable ex) {
                        log.error("getClassSignatures error, ref:{}", e.getNameAsString());
                        return null;
                    }
                }).filter(StringUtils::isBlank)
                .collect(Collectors.toSet());
    }

    private ParserConfiguration buildJavaParserConfig(List<String> sourcePath) {
        TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver(reflectionTypeSolver);
        sourcePath.forEach(path -> {
            try {
                TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(new File(path));
                combinedSolver.add(javaParserTypeSolver);
            } catch (Exception e) {
                log.error("load source path error, path:{}", path);
            }
        });
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
        return new ParserConfiguration().setSymbolResolver(symbolSolver);
    }


    private List<String> findAllSourceRootPath(File rootDir) {
        log.info("findAllSourceRootPath, dir:{}", rootDir.getAbsolutePath());
        List<String> sourceRootPathList = Lists.newArrayList();
        File[] files = rootDir.listFiles(File::isDirectory);
        if (null == files) {
            return Collections.emptyList();
        }
        for (File file : files) {
            if ("src".equals(file.getName())) {
                sourceRootPathList.add(file.getAbsolutePath() + "/main/java");
            } else {
                File[] srcFiles = file.listFiles((dir, name) -> "src".equals(name));
                if (null != srcFiles) {
                    for (File srcFile : srcFiles) {
                        sourceRootPathList.add(srcFile.getAbsolutePath() + "/main/java");
                    }
                }
            }
        }
        return sourceRootPathList;
    }
}

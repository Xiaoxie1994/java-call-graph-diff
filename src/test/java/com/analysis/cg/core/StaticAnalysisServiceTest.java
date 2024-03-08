package com.analysis.cg.core;

import com.analysis.cg.core.source.StaticAnalysisService;
import com.analysis.cg.manager.GraphvizManager;
import com.analysis.cg.model.AstEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Set;

@SpringBootTest
class StaticAnalysisServiceTest {

    @Resource
    private StaticAnalysisService staticAnalysisService;

    @Resource
    private GraphvizManager graphvizManager;

    @Test
    public void test() {
        // use ssh need config 'tmp/key/id_rsa'
        String gitPath = "https://github.com/Xiaoxie1994/JavaCallGraph.git";
        // draw cg
        AstEntity astEntity = staticAnalysisService.methodCallGraph(gitPath);
        graphvizManager.drawGraph(astEntity, Collections.emptySet());
        // draw change cg
        String diffBranchName = "test-code-change";
        Set<String> changeMethods = staticAnalysisService.codeChangeMethods(gitPath, diffBranchName);
        if (CollectionUtils.isNotEmpty(changeMethods)) {
            graphvizManager.drawGraph(astEntity, changeMethods);
        }
    }
}
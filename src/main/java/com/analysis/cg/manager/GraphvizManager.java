package com.analysis.cg.manager;

import com.analysis.cg.model.AstEntity;
import com.google.common.collect.Lists;
import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.Renderer;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Node;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class GraphvizManager {

    private static final String FILE_PATH = "tmp/result";

    public void drawGraph(AstEntity astEntity, Set<String> changeMethods) {
        if (null == astEntity || CollectionUtils.isEmpty(astEntity.getEndPointMethodSignatures())) {
            return;
        }
        Map<String, AstEntity.MethodDeclareInfo> signature2MethodDeclareMap = astEntity.getSignature2MethodDeclareMap();
        astEntity.getEndPointMethodSignatures().forEach(endPoint -> {
            AstEntity.MethodDeclareInfo startMethod = signature2MethodDeclareMap.get(endPoint);
            if (null == startMethod || CollectionUtils.isEmpty(startMethod.getCallMethodSignatures())) {
                return;
            }
            Node startNode = getNode(changeMethods, endPoint, startMethod);
            List<Node> links = Lists.newArrayList();
            startMethod.getCallMethodSignatures()
                    .forEach(signature -> doDraw(signature2MethodDeclareMap, links, startNode, signature, changeMethods));
            if (CollectionUtils.isEmpty(links)) {
                return;
            }
            Graph graph = Factory.graph(endPoint).directed().with(links);
            Renderer render = Graphviz.fromGraph(graph).height(1000).render(Format.PNG);

            try {
                if (CollectionUtils.isEmpty(changeMethods)) {
                    render.toFile(new File(FILE_PATH + "/static/" + endPoint + ".png"));
                } else {
                    render.toFile(new File(FILE_PATH + "/change/" + endPoint + ".png"));
                }
            } catch (Exception e) {
                log.error("graph draw error, endpoint:{}", endPoint);
            }
        });
    }

    private Node getNode(Set<String> changeMethods, String signature, AstEntity.MethodDeclareInfo method) {
        Node startNode = Factory.node(method.getClassSimpleName() + "." + method.getSimpleName());
        if (changeMethods.contains(signature)) {
            startNode = startNode.with(Color.RED);
        }
        return startNode;
    }

    private void doDraw(Map<String, AstEntity.MethodDeclareInfo> signature2MethodDeclareMap,
                        List<Node> links,
                        Node preNode,
                        String nextMethodSignature,
                        Set<String> changeMethods) {
        AstEntity.MethodDeclareInfo nextMethod = signature2MethodDeclareMap.get(nextMethodSignature);
        if (null == nextMethod) {
            return;
        }
        Node nextNode = getNode(changeMethods, nextMethodSignature, nextMethod);
        links.add(preNode.link(Factory.to(nextNode)));
        if (CollectionUtils.isEmpty(nextMethod.getCallMethodSignatures())) {
            return;
        }
        nextMethod.getCallMethodSignatures().forEach(signature -> {
            doDraw(signature2MethodDeclareMap, links, nextNode, signature, changeMethods);
        });
    }
}

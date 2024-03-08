package com.analysis.cg.model;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class AstEntity {

    private Set<String> endPointMethodSignatures = Sets.newHashSet();

    private Map<String, InterfaceOrClassDeclareInfo> signature2InterfaceOrClassDeclareMap = Maps.newHashMap();

    private Map<String, MethodDeclareInfo> signature2MethodDeclareMap = Maps.newHashMap();

    @Data
    public static class InterfaceOrClassDeclareInfo {
        private String classFileRelativePath;
        private String simpleName;
        private String signature;
        private boolean isInterface;
        private boolean isAbstract;
        private Set<String> implementInterfaceSignatures = Collections.emptySet();
        private Set<String> extendClassSignatures = Collections.emptySet();
    }

    @Data
    public static class MethodDeclareInfo {
        private String simpleName;
        private boolean isPublic;
        private String classSimpleName;
        private String signature;
        private List<String> paramTypeList = Collections.emptyList();
        private Set<String> annotationSimpleNames = Collections.emptySet();
        private List<String> callMethodSignatures = Collections.emptyList();
    }
}

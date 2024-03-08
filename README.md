# Introduction
Java implementation of CallGraph.  
CG的Java实现，采用源码和字节码方式。提供静态调用关系图和分支变更影响分析。

# 实现思路
![img.png](picture/impl.png)

# 测试方式
项目测试入口：com.analysis.cg.core.StaticAnalysisServiceTest
- CG  
核心方法：com.analysis.cg.core.source.StaticAnalysisService.methodCallGraph
![img.png](picture/cg.png)
- 变更影响CG(生产依赖静态CG)  
核心方法：com.analysis.cg.core.source.StaticAnalysisService.codeChangeMethods
![img.png](picture/change_cg.png)





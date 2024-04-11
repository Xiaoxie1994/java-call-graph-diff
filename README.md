# JavaCallGraph
> Java implementation of CallGraph.

CG的Java实现方式。采用源码和字节码静态分析方式，提供静态调用关系图和分支变更影响分析能力。

## 实现思路
![img.png](picture/impl.png)

## 工程结构
TBD

## 测试方式
测试入口：com.analysis.cg.core.StaticAnalysisServiceTest
- 生成静态CG  
**核心方法**：com.analysis.cg.core.source.StaticAnalysisService.methodCallGraph
<img src="picture/cg.png" width="500" height="500" alt="callGraph">

- 变更影响CG(依赖静态CG)    
**核心方法**：com.analysis.cg.core.source.StaticAnalysisService.codeChangeMethods
![img.png](picture/change_cg.png)

## Connect with Me
- Email: [xiexiao064@gmail.com](mailto:xiexiao064@gmail.com)
- WeChat: ShawnLFF

License
---

This code is distributed under the MIT license. See `LICENSE` in this directory.




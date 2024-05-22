# java-call-graph-diff
[![Java support](https://img.shields.io/badge/Java-8-green?logo=java&logoColor=white)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/license-MIT-blue?logo=opensourceinitiative&logoColor=white)](https://opensource.org/licenses/MIT)

> Java implementation of CallGraph.

CG的Java实现方式。采用源码静态分析方式，提供静态调用关系图生产和代码变更影响面分析能力。

## 实现思路
![img.png](picture/impl.png)

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




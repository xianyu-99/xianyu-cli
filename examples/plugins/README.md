# Plugin Template

YuCLI 可以生成一个最小 Java ServiceLoader 插件工程：

```text
/plugin template demo-tools
```

该命令会在当前工作目录创建 `demo-tools-yucli-plugin/`。生成工程包含：

- `pom.xml`
- `.gitignore`
- `README.md`
- 一个 `YuPlugin` 实现
- `META-INF/services/com.yucli.plugin.YuPlugin`
- 一个名为 `echo` 的示例工具

构建和安装流程：

```bash
# 先在 YuCLI 源码仓库里，把 YuCLI API 安装到本地 Maven 仓库。
mvn -q -DskipTests install

# 再进入生成的插件工程构建。
mvn -q package
mkdir -p ~/.YuCLI/plugins
cp target/demo-tools-yucli-plugin-0.1.0.jar ~/.YuCLI/plugins/
```

PowerShell：

```powershell
mvn -q -DskipTests install
mvn -q package
New-Item -ItemType Directory -Force $HOME\.YuCLI\plugins
Copy-Item target\demo-tools-yucli-plugin-0.1.0.jar $HOME\.YuCLI\plugins\
```

然后在 YuCLI 中执行：

```text
/plugin reload
/plugin enable demo-tools
```

示例工具会注册为 `plugin__demo-tools__echo`。

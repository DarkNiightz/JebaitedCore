This folder is a convenient local deploy target for JebaitedCore.jar.

Option A — Maven (auto-copy after package):
  1) Copy jebaited-deploy.properties.example to jebaited-deploy.properties (next to pom.xml)
  2) Set jebaited.deploy.plugins.dir to this full path using forward slashes, e.g.:
       jebaited.deploy.plugins.dir=C:/Users/you/.../JebaitedCore/local-paper-server/plugins
  3) From the JebaitedCore module directory: ..\mvnw.cmd clean package

Option B — Script:
  From the module directory:
    .\scripts\deploy-jebaited.ps1 -PluginsDir "<full path to this plugins folder>"

Point your Paper server at this directory, or copy JebaitedCore.jar from target\ to your real server.

# 强制推送 .github 工作流到 GitHub

如果 GitHub 显示 .github 文件夹被隐藏，在项目目录执行：

```powershell
cd "d:\个人工具\背单词"

# 强制添加 .github（即使被忽略也会添加）
git add -f .github/

# 查看是否已加入暂存区
git status

# 提交并推送
git commit -m "添加 GitHub Actions 构建工作流"
git push
```

推送成功后，刷新仓库的 Actions 页面即可看到 Build APK。

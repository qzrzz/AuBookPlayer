import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import chalk from 'chalk';

/**
 * 项目根路径与关键文件路径定义
 */
const ROOT_DIR = process.cwd();
const GRADLE_FILE_PATH = path.join(ROOT_DIR, 'app', 'build.gradle');

/**
 * 执行终端 Shell 命令并输出日志
 *
 * @param {string} command 需要执行的 Shell 命令
 * @param {string} [errorMessage] 执行失败时的自定义提示信息
 * @param {boolean} [ignoreError=false] 是否在错误时抛出或仅警告
 */
function runCommand(command, errorMessage = '命令执行失败', ignoreError = false) {
  console.log(chalk.gray(`$ ${command}`));
  try {
    execSync(command, { stdio: 'inherit', cwd: ROOT_DIR });
    return true;
  } catch (error) {
    if (ignoreError) {
      console.log(chalk.yellow(`⚠️ [警告] ${errorMessage}`));
      return false;
    }
    console.error(chalk.red(`\n✖ ${errorMessage}`));
    console.error(chalk.red(error.message));
    process.exit(1);
  }
}

/**
 * 解析 app/build.gradle 中的 versionName 与 versionCode
 *
 * @returns {{ versionName: string, versionCode: number, gradleContent: string }} 返回当前版本名、版本号及文件内容
 */
function parseGradleVersion() {
  const gradleContent = fs.readFileSync(GRADLE_FILE_PATH, 'utf-8');

  // 使用正则表达式匹配 versionName 和 versionCode
  const versionNameMatch = gradleContent.match(/versionName\s+["']([^"']+)["']/);
  const versionCodeMatch = gradleContent.match(/versionCode\s+(\d+)/);

  if (!versionNameMatch || !versionCodeMatch) {
    console.error(chalk.red('✖ 错误：无法在 app/build.gradle 中定位 versionName 或 versionCode！'));
    process.exit(1);
  }

  return {
    versionName: versionNameMatch[1],
    versionCode: parseInt(versionCodeMatch[1], 10),
    gradleContent,
  };
}

/**
 * 根据类型或指定参数计算递增后的新版本号
 *
 * @param {string} currentVersionName 当前版本名称 (例如 "5.2.0")
 * @param {number} currentVersionCode 当前版本代码 (例如 76)
 * @param {string} [bumpTypeInput='patch'] 递增类型 ('patch' | 'minor' | 'major') 或具体的目标版本号 (例如 "5.3.0")
 * @returns {{ newVersionName: string, newVersionCode: number }} 返回新版本名称与版本代码
 */
function calculateNextVersion(currentVersionName, currentVersionCode, bumpTypeInput = 'patch') {
  // 如果直接传入了合法的 SemVer 版本号 (例: 5.3.0)
  if (/^\d+\.\d+\.\d+$/.test(bumpTypeInput)) {
    return {
      newVersionName: bumpTypeInput,
      newVersionCode: currentVersionCode + 1,
    };
  }

  // 拆分主版本号、次版本号、修订号
  const versionParts = currentVersionName.split('.').map((num) => parseInt(num, 10));
  let [major, minor, patch] = versionParts;

  if (isNaN(major) || isNaN(minor) || isNaN(patch)) {
    console.error(chalk.red(`✖ 错误：当前版本号 "${currentVersionName}" 不符合语义化版本格式 (X.Y.Z)`));
    process.exit(1);
  }

  // 根据 bumpType 进行递增
  switch (bumpTypeInput.toLowerCase()) {
    case 'major':
      major += 1;
      minor = 0;
      patch = 0;
      break;
    case 'minor':
      minor += 1;
      patch = 0;
      break;
    case 'patch':
    default:
      patch += 1;
      break;
  }

  const newVersionName = `${major}.${minor}.${patch}`;
  const newVersionCode = currentVersionCode + 1;

  return { newVersionName, newVersionCode };
}

/**
 * 更新 app/build.gradle 中的 versionName 与 versionCode
 *
 * @param {string} newVersionName 新版本名称
 * @param {number} newVersionCode 新版本代码
 * @param {string} originalContent 原始 build.gradle 文件内容
 */
function updateGradleFile(newVersionName, newVersionCode, originalContent) {
  let updatedContent = originalContent.replace(
    /versionName\s+["']([^"']+)["']/,
    `versionName "${newVersionName}"`
  );
  updatedContent = updatedContent.replace(
    /versionCode\s+(\d+)/,
    `versionCode ${newVersionCode}`
  );

  fs.writeFileSync(GRADLE_FILE_PATH, updatedContent, 'utf-8');
  console.log(
    chalk.bold.green(`✔ 已写入 app/build.gradle: versionName -> "${newVersionName}", versionCode -> ${newVersionCode}`)
  );
}

/**
 * 自动提交 Git 更改并创建附注 Tag
 *
 * @param {string} newVersionName 新版本名称
 * @param {number} newVersionCode 新版本代码
 */
function commitAndTagGit(newVersionName, newVersionCode) {
  const tagName = `v${newVersionName}`;
  const commitMessage = `bump: 升级版本号至 ${tagName} (versionCode ${newVersionCode})`;

  console.log(chalk.bold.blue(`\n📦 [1/4] 正在提交 Git 更改并创建 Tag: ${tagName}...`));

  // 1. 暂存相关修改文件
  runCommand('git add app/build.gradle package.json .gitignore scripts/', 'Git add 失败');

  // 2. 提交 Conventional Commits 信息
  runCommand(`git commit -m "${commitMessage}"`, 'Git commit 失败');

  // 3. 创建附注标签
  runCommand(`git tag -a ${tagName} -m "Release ${tagName}"`, 'Git tag 失败');

  console.log(chalk.green(`✔ 成功提交 Commit 并打上 Git Tag ${tagName}`));

  // 4. 尝试推送远程
  console.log(chalk.blue('📡 正在尝试推送 Commit 和 Tag 到远程仓库...'));
  const pushMainSuccess = runCommand('git push origin main', '推送 main 分支跳过或失败', true);
  const pushTagSuccess = runCommand(`git push origin ${tagName}`, `推送 Tag ${tagName} 跳过或失败`, true);

  if (pushMainSuccess && pushTagSuccess) {
    console.log(chalk.green('✔ 远程仓库推送成功！'));
  } else {
    console.log(chalk.yellow('ℹ 您可以在发布完成后手动运行: git push origin main && git push --tags'));
  }
}

/**
 * 通过 Gradle 编译 Release APK，并由脚本生成规范名称副本 AuBookPlayer-vX.Y.Z.apk
 *
 * @param {string} newVersionName 新版本名称 (例如 "5.2.0")
 * @returns {string} 返回准备发布的规范名称 APK 绝对路径
 */
function buildReleaseApk(newVersionName) {
  console.log(chalk.bold.blue('\n🔨 [2/4] 正在使用 Gradle 编译 Release APK (./gradlew assembleRelease)...'));

  runCommand('./gradlew assembleRelease', 'Gradle 编译 Release APK 失败');

  // 寻找生成的 APK 输出目录
  const apkDir = path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'release');
  if (!fs.existsSync(apkDir)) {
    console.error(chalk.red(`✖ 找不到 APK 构建输出目录: ${apkDir}`));
    process.exit(1);
  }

  // 获取生成的原始 apk 文件
  const apkFiles = fs
    .readdirSync(apkDir)
    .filter((file) => file.endsWith('.apk'))
    .map((file) => path.join(apkDir, file));

  if (apkFiles.length === 0) {
    console.error(chalk.red('✖ 构建输出路径中未找到任何 APK 文件！'));
    process.exit(1);
  }

  const rawApkPath = apkFiles[0];
  const targetApkName = `AuBookPlayer-v${newVersionName}.apk`;
  const targetApkPath = path.join(apkDir, targetApkName);

  // 在发布脚本层将编译好的 APK 复制一份为规范的 Release 名称
  fs.copyFileSync(rawApkPath, targetApkPath);

  console.log(chalk.bold.green(`✔ APK 编译完成，发布脚本已生成规范命名文件！`));
  console.log(chalk.cyan(`   规范文件名: ${targetApkName}`));
  console.log(chalk.cyan(`   上传路径:   ${targetApkPath}`));
  return targetApkPath;
}

/**
 * 自动尝试从 Git 远程 origin 地址中解析 GitHub 仓库名 (例如: owner/repo)
 *
 * @returns {string | null} 解析成功返回 "owner/repo"，失败返回 null
 */
function getGithubRepo() {
  try {
    const remoteUrl = execSync('git remote get-url origin', { encoding: 'utf-8', cwd: ROOT_DIR }).trim();
    // 匹配如 git@github.com-qzrzz:qzrzz/AuBookPlayer.git 或 https://github.com/qzrzz/AuBookPlayer.git
    const match = remoteUrl.match(/[:/]([^/]+\/[^/.]+)(?:\.git)?$/);
    if (match) {
      return match[1];
    }
  } catch {
    // 忽略获取失败
  }
  return null;
}

/**
 * 使用 GitHub CLI (`gh`) 创建 Release 并上传生成的 APK 文件
 *
 * @param {string} tagName 标签名称 (例如 "v5.3.0")
 * @param {string} apkPath APK 文件的绝对路径
 */
function publishGithubRelease(tagName, apkPath) {
  console.log(chalk.bold.blue(`\n🚀 [3/4] 正在通过 gh CLI 创建 GitHub Release 并上传 APK...`));

  // 检查 GitHub CLI 环境
  try {
    execSync('gh --version', { stdio: 'ignore' });
  } catch {
    console.log(chalk.yellow('⚠️ 未检测到 gh CLI 命令，跳过 GitHub Release 上传步骤'));
    return;
  }

  // 自动获取 --repo 参数以防止 SSH 别名导致 gh 找不到默认仓库
  const repoName = getGithubRepo();
  const repoFlag = repoName ? `--repo "${repoName}" ` : '';

  // 执行 gh release create
  const ghCommand = `gh release create "${tagName}" "${apkPath}" ${repoFlag}--title "${tagName}" --generate-notes`;
  const success = runCommand(ghCommand, 'GitHub Release 创建或上传失败', true);

  if (success) {
    console.log(chalk.bold.green(`✔ GitHub Release ${tagName} 已经创建成功并挂载 APK 附件！`));
  } else {
    console.log(chalk.yellow(`ℹ 您稍后也可以手动运行:\n  gh release create "${tagName}" "${apkPath}" ${repoFlag}--title "${tagName}" --generate-notes`));
  }
}

/**
 * 打印帮助提示信息
 */
function printHelp() {
  console.log(`
${chalk.bold.cyan('AuBookPlayer 自动化版本发布工具')}

${chalk.bold('用法:')}
  bun run release [patch | minor | major | <指定版本号>]

${chalk.bold('示例:')}
  bun run release           # 自动递增 patch 版本 (例如 5.2.0 -> 5.2.1)
  bun run release minor     # 自动递增 minor 版本 (例如 5.2.0 -> 5.3.0)
  bun run release major     # 自动递增 major 版本 (例如 5.2.0 -> 6.0.0)
  bun run release 5.3.0     # 指定升级为 5.3.0 版本

${chalk.bold('全流程自动化功能:')}
  1. 自动解析与自增 app/build.gradle 中的 versionName 与 versionCode
  2. 自动 Git Commit 提交与 Git Tag 打标签 (约定式提交规范)
  3. 自动执行 ./gradlew assembleRelease 编译打包 APK
  4. 自动创建 GitHub Release 并上传 APK 资产文件
`);
}

/**
 * 脚本程序主入口
 */
function main() {
  const arg = process.argv[2];

  if (arg === '--help' || arg === '-h') {
    printHelp();
    process.exit(0);
  }

  console.log(chalk.bold.magenta('\n=================================================='));
  console.log(chalk.bold.cyan('  👑 尊敬的主人，AuBookPlayer 自动化发布流程启动'));
  console.log(chalk.bold.magenta('==================================================\n'));

  // 1. 读取当前版本号
  const { versionName, versionCode, gradleContent } = parseGradleVersion();
  console.log(chalk.gray(`当前版本信息: versionName="${versionName}", versionCode=${versionCode}`));

  // 2. 计算新版本号
  const bumpType = arg || 'patch';
  const { newVersionName, newVersionCode } = calculateNextVersion(versionName, versionCode, bumpType);
  console.log(
    chalk.bold.yellow(`即将升级目标: versionName="${newVersionName}", versionCode=${newVersionCode}`)
  );

  // 3. 修改并保存 build.gradle
  updateGradleFile(newVersionName, newVersionCode, gradleContent);

  // 4. Git 提交 & 打 Tag
  commitAndTagGit(newVersionName, newVersionCode);

  // 5. 编译 Release APK
  const apkPath = buildReleaseApk(newVersionName);

  // 6. 发布到 GitHub Release 并上传 APK
  publishGithubRelease(`v${newVersionName}`, apkPath);

  console.log(chalk.bold.magenta('\n=================================================='));
  console.log(chalk.bold.green(' ✨ 主人，自动化版本升级与发布全流程已圆满完成！✨'));
  console.log(chalk.bold.magenta('==================================================\n'));
}

main();

# 中国象棋AI助手 (Chinese Chess Helper)

一款基于Android无障碍服务 + 悬浮窗的中国象棋辅助程序，通过截屏识别棋盘并实时给出AI推荐走法。

---

## 🏗️ 项目架构

```
ChineseChessHelper/
├── app/
│   ├── build.gradle                    # 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml         # 应用清单
│       ├── java/com/catpaw/chesshelper/
│       │   ├── ChessApplication.java   # 应用入口
│       │   ├── service/               # 服务层
│       │   │   ├── ChessAccessibilityService.java  # 无障碍识别服务
│       │   │   ├── FloatingWindowService.java       # 悬浮窗服务
│       │   │   └── ScreenCaptureService.java        # 截屏服务
│       │   ├── ai/                    # AI引擎层
│       │   │   ├── ChessEngine.java                # 本地Alpha-Beta引擎
│       │   │   └── CloudAIProvider.java            # 云端AI适配
│       │   ├── model/                 # 数据模型
│       │   │   ├── ChessPiece.java                 # 棋子模型
│       │   │   ├── BoardRecognitionResult.java     # 识别结果
│       │   │   └── FENGenerator.java               # FEN格式转换
│       │   ├── config/                # 配置管理
│       │   │   └── AppConfig.java                  # 偏好配置
│       │   ├── ui/                    # 界面层
│       │   │   ├── MainActivity.java               # 主界面
│       │   │   └── SettingsActivity.java           # 设置界面
│       │   └── util/                  # 工具类
│       │       └── ImageProcessor.java             # 图像识别处理
│       └── res/                       # 资源文件
│           ├── layout/                # 布局文件
│           ├── xml/                   # 配置文件(无障碍/preferences)
│           ├── values/                # 字符串/主题/颜色
│           └── drawable/              # 图标资源
├── build.gradle                        # 顶层构建配置
├── settings.gradle                     # 项目设置
└── gradle.properties                   # Gradle属性
```

---

## 🔧 核心模块说明

### 1. 无障碍识别服务 (ChessAccessibilityService)

| 功能 | 实现方式 |
|------|----------|
| 屏幕变化监听 | 通过 `onAccessibilityEvent()` 监听 `TYPE_WINDOW_CONTENT_CHANGED` |
| 棋子识别截屏 | 调用 `ScreenCaptureService` 使用 MediaProjection API |
| 棋盘区域检测 | 基于颜色特征自动检测/手动框选两种模式 |
| UCCI走法解析 | 解析棋子坐标 (列a-i, 行0-9) |

### 2. 悬浮窗服务 (FloatingWindowService)

透明悬浮窗，实时显示：
- 当前步数
- 双方行棋方（红/黑）
- AI推荐最佳走法
- 局面评分（正=红优，负=黑优）
- FEN字符串

支持触屏拖拽移动位置。

### 3. 本地AI引擎 (ChessEngine) - Alpha-Beta搜索

#### 搜索算法
```
1. 迭代加深搜索 (Iterative Deepening)
   └─ 从深度1开始逐步加深，超时则取前一层最优

2. Alpha-Beta剪枝 (Negamax形式)
   └─ 减少搜索节点数

3. 置换表 (Transposition Table)
   └─ 哈希相同局面，避免重复搜索

4. Killer Move启发式
   └─ 优先搜索上次引起截断的走法

5. 静态搜索 (Quiescence Search)
   └─ 只搜索吃子走法，消除"水平线效应"
```

#### 评估函数
```
局面评分 = 棋子基础价值 + 位置价值 + 机动性评分 + 王安全评分

- 车=1000, 马=480, 炮=500, 象=240, 士=240, 兵=100（过河后递增）
- 位置价值矩阵独立配置（兵/马/炮/车各有10x9矩阵）
- 兵卒过河后价值×2到×3
```

#### FEN格式（中国象棋标准）
```
rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w
│││││││││ ││ │││││││ │││││││││ │ │ │││││││││ ││ │││││││││ │
│││││││││ │  │       │           │ │ │         │  │           │
9列黑方    空格 黑炮      黑兵列     空 空 红兵列     空格 红炮     红方列
```

### 4. 云端AI对接 (CloudAIProvider)

#### 支持的API
| 提供商 | 接口格式 | 说明 |
|--------|----------|------|
| OpenAI | `v1/chat/completions` | GPT-4 / GPT-4o / o1 |
| DeepSeek | `v1/chat/completions` | 兼容OpenAI接口 |
| 其他 | 自定义URL | 兼容OpenAI Chat API的任何服务 |

#### Prompt设计
```
你是一个中国象棋特级大师。分析以下象棋局面并给出最佳着法。
FEN: rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w
轮到: 红方走棋
请直接回答UCCI格式的最佳着法，只输出走法，不要解释。
```

#### UCCI响应提取
- 正则匹配 `[a-i][0-9][a-i][0-9]`
- 如 `h2h3`（炮二平五）

---

## 🚀 使用指南

### 快速开始

1. **安装并打开应用**
2. **授予权限**（按提示操作）
   - 🔴 无障碍服务权限（识别棋盘）
   - 🔴 SYSTEM_ALERT_WINDOW 悬浮窗权限
   - 🔴 MediaProjection 截屏授权
3. **进入象棋APP**（QQ象棋、天天象棋等）
4. **启动悬浮窗** → AI自动分析并推荐走法

### 设置AI提供商

进入「设置」→「AI引擎设置」→ 选择：
- **本地引擎**：离线计算，不需网络，速度快但棋力中等（取决于搜索深度）
- **OpenAI**：在线调用GPT-4，棋力强但需要API Key和联网
- **自定义API**：填入兼容OpenAI接口的任意API地址和Key

### 性能调优
- 搜索深度：4-6层中等难度，8层较慢但更强
- 识别间隔：建议1500ms（过快会增加耗电）
- 棋盘区域：先自动检测，不准确时手动框选

---

## 📐 AI走棋算法思路详解

### 整体流程
```
无障碍事件触发
       │
       ▼
截屏获取棋盘图像
       │
       ▼
图像识别 → 90维棋盘数组 → FEN字符串
       │
       ▼
检测是否有棋步变化（FEN是否改变）
       │
       ▼
调用AI引擎（本地Alpha-Beta / 云端API）
       │
       ▼
解析走法结果 → 更新悬浮窗 → 等待下一次识别
```

### 本地算法核心伪代码
```java
// 迭代加深
for (depth = 1; depth <= maxDepth; depth++) {
    bestMove = searchRoot(depth);
    if (超时 || 找到必杀) break;
}

// Alpha-Beta搜索
int alphaBeta(int depth, int alpha, int beta, boolean isMax) {
    if (depth == 0) return quiescenceSearch(alpha, beta);
    
    moves = generateLegalMoves();  // 生成合法走法
    sortMoves(moves);              // 排序：吃子走法优先
    
    for (move in moves) {
        makeMove(move);
        score = -alphaBeta(depth-1, -beta, -alpha, !isMax);
        unmakeMove(move);
        
        if (score >= beta) return beta;   // Beta截断
        if (score > alpha) alpha = score; // 更新最优值
    }
    return alpha;
}

// 局面评估
int evaluate() {
    score = 0;
    for (each square) {
        piece = board[square];
        score += pieceValue[piece];       // 基础价值
        score += positionValue[piece];    // 位置价值（10x9矩阵查表）
    }
    score += mobility * 0.1;              // 机动性
    score += kingSafety * 0.3;            // 王安全
    return score;
}
```

---

## 🔌 第三方象棋APP兼容性

| APP名称 | 识别方式 | 状态 |
|---------|----------|------|
| QQ象棋 | 图像识别 | ✅ 支持 |
| 天天象棋 | 图像识别 | ✅ 支持 |
| 中国象棋(微游) | 图像识别 | ✅ 支持 |
| 自建棋盘 | 图像识别 | ⚠️ 需要训练模板 |

注意事项：
- 不同APP的棋子样式不同，可能需要调整图像识别参数
- 部分APP可能会禁用无障碍服务，需要切换识别方式
- 建议在"手动模式"下框选固定的棋盘区域

---

## ⚙️ 技术栈

- **Android SDK**: minSdk 24, targetSdk 34
- **Java**: JDK 17
- **网络**: OkHttp4 + Gson
- **图像**: Android Bitmap API + 颜色特征识别
- **搜索**: Alpha-Beta + 迭代加深 + 置换表

---

## 🔒 隐私与安全

- 截屏数据仅在本地处理，不会上传
- 云端API调用时仅发送FEN字符串，不含图像数据
- 支持纯离线模式（本地引擎）

---

## 📄 License

本项目仅供学习交流，禁止用于商业比赛作弊等违规行为。

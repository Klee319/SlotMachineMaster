# SlotMachineMaster

Minecraft Paper サーバー向けのスロットマシンプラグイン。JSONC設定ファイルだけでオリジナルのスロット台を自由に作成できます。

## 動作環境

- **Paper** 1.21+ (Java 16+)
- **Vault** + 経済プラグイン (EssentialsX 等)

## 導入方法

1. ビルド済み JAR を `plugins/` フォルダに配置
2. サーバーを起動（初回起動時にテンプレート設定が自動生成されます）
3. 生成された `plugins/slotMachinePlugin/slotConfigs/` 内の JSONC ファイルを編集してスロットを定義
4. `/slot reload` で設定をリロード

## ビルド方法

```bash
mvn clean package
```

`target/slotMachinePlugin-1.0-SNAPSHOT.jar` が生成されます。

---

## コマンド一覧

| コマンド | 説明 |
|---------|------|
| `/slot help` | ヘルプ表示 |
| `/slot list` | 読み込み済みスロット設定を一覧表示 |
| `/slot set <マシンID> <設定パス>` | ボタンにスロット設定を紐付け |
| `/slot delete <マシンID>` | マシンを削除 |
| `/slot debug <マシンID> <回数>` | テスト回転（非同期実行、10%ごとに進捗表示） |
| `/slot itemstack` | 手持ちアイテムの Base64 文字列を取得 |
| `/slot analytics topSlot [日数]` | スロット別の収益ランキング |
| `/slot analytics topUser [日数]` | ユーザー別の収益ランキング |
| `/slot reload` | 全設定ファイルをリロード |

## マシンの設置方法

1. **壁**にボタンを設置（天井・床ボタンは非対応）
2. ボタンの向きに応じて、3つの額縁を横一列に設置（ボタンと同じ壁面に配置）
3. `/slot set <任意のマシンID> <スロット設定パス>` を実行
4. ボタンを右クリックでスロット開始

---

## スロット設定ガイド

### ファイル構成

```
plugins/slotMachinePlugin/
├── config.json              # プラグイン全体設定
├── slotConfigs/             # スロット設定ファイル
│   ├── template.jsonc       # テンプレート
│   ├── mining_slot/         # サブフォルダでグループ化可能
│   │   ├── main.jsonc
│   │   ├── wood.jsonc
│   │   └── ...
│   └── dragon_rush/
│       ├── normal.jsonc
│       └── ...
├── itemConfigs/             # カスタムアイテム定義
│   └── items_template.jsonc
└── machines.json            # マシン状態（自動管理）
```

設定パスはフォルダ区切りの相対パス（拡張子不要）で指定します：
- `template` → `slotConfigs/template.jsonc`
- `mining_slot/main` → `slotConfigs/mining_slot/main.jsonc`
- `dragon_rush/normal` → `slotConfigs/dragon_rush/normal.jsonc`

### config.json（プラグイン設定）

```jsonc
{
  "entryFileNames": ["main", "normal"]  // タブ補完の候補に表示するエントリファイル名
}
```

`entryFileNames` に指定したファイル名を持つスロット設定が `/slot set` のタブ補完候補に優先表示されます。

---

## スロット設定ファイルの書き方（JSONC）

JSONC 形式を採用しているため、`//` や `/* */` のコメントが使えます。

### 基本構造

```jsonc
{
  // === 基本設定 ===
  "reels": 3,              // リール数（現在は3固定）
  "shuffleTime": 2.5,      // 回転時間（秒）
  "shuffleSpeed": 1.2,     // 回転速度（大きいほど速い）
  "spinSpeed": 1.0,        // 停止間隔速度（大きいほど速い）
  "spinCost": 500,         // 1回転のコスト（通貨）
  "itemCost": {            // アイテムコスト（省略可）
    "name": "GOLD_INGOT",  // マテリアル名 or カスタムアイテムキー
    "amount": 5             // 必要個数
  },

  // === ハズレ時設定 ===
  "loseStockOperation": "ADD",       // ADD / SUB / SET
  "loseStockValue": 10,              // ストック調整値
  "loseMessage": "&cハズレ!",        // ハズレ時メッセージ

  // === サウンド ===
  "defaultSoundSettings": { ... },

  // === 変数 ===
  "variables": [ ... ],

  // === グローバルイベント ===
  "event": [ ... ],

  // === パーティクル（回転中） ===
  "defaultParticleSettings": [ ... ],

  // === 当選パターン ===
  "patterns": [ ... ]
}
```

---

### 当選パターン（patterns）

パターンはスロットの核心部分です。各パターンは独立した当選確率を持ち、抽選は重み付きランダムで行われます。

```jsonc
"patterns": [
  {
    "probability": 50.0,   // 当選確率（%）- 変数式も可（例："probability/8"）
    "items": [             // 揃うアイテム（3つ）
      "DIAMOND", "DIAMOND", "DIAMOND"
    ],

    // --- 報酬 ---
    "rewards": [
      {
        "type": "money",              // "money" または "item"
        "value": "stock*1000"         // 金額（変数式可）
      },
      {
        "type": "item",
        "value": "GOLD_INGOT",        // マテリアル名 or カスタムアイテムキー
        "quantity": 5                  // 付与個数
      }
    ],

    // --- 表示 ---
    "winMessage": "&6大当たり! &e¥<profit>",  // 当選メッセージ
    //   <profit> = 獲得金額, <playerName> = プレイヤー名, <変数名> = 変数値

    // --- ストック操作 ---
    "stockOperation": "ADD",   // ADD / SUB / SET
    "stockValue": 50,

    // --- スロット切替 ---
    "nextSlotOnWin": "mining_slot/wood",  // 当選時に別のスロット設定に遷移

    // --- サウンド ---
    "patternSound": {
      "type": "minecraft:block.note_block.chime",
      "volume": 1.0,
      "pitch": 1.2,
      "radius": 2.0      // -1=回した人のみ, -2=全プレイヤー
    },

    // --- 全体通知 ---
    "broadcastSettings": {
      "message": "&a<playerName>が大当たり！",
      "broadcastSound": {
        "type": "minecraft:entity.experience_orb.pickup",
        "volume": 1.0, "pitch": 1.0, "radius": -2
      }
    },

    // --- パーティクル（当選時） ---
    "particleSettings": [
      {
        "point": "frame",        // "frame"（リール位置） or "button"（ボタン位置）
        "particle": "END_ROD",   // パーティクルID
        "count": 30,
        "speed": 0.2,
        "offset": [0.5, 0.5, 0.5],   // [横, 上, 正面] の拡散範囲
        "color": [1.0, 0.0, 0.0]     // RGB（DUST パーティクル専用, 0.0〜1.0）
      }
    ],

    // --- パターン内イベント ---
    "event": [ ... ]  // 当選時に条件付きで追加処理を実行
  }
]
```

**確率の仕組み**: 全パターンの `probability` を合計し、その中から重み付きランダムで1つ選ばれます。どのパターンにも当選しなかった場合はハズレになります。

**アイテム指定**: `items` にはマテリアル名（`DIAMOND`, `GOLD_INGOT` 等）またはカスタムアイテムキー（後述）を指定します。

---

### 変数システム（variables）

マシンごとに永続化される変数を定義できます。変数はスロット遷移時にも引き継がれます。

```jsonc
"variables": [
  { "varName": "gameCount", "initialValue": 0 },
  { "varName": "HP", "initialValue": 10 }
]
```

- **初期化**: マシン初回スピン時のみ `initialValue` が設定される
- **永続化**: スピンごとに `machines.json` に保存される
- **引き継ぎ**: `nextSlotOnWin` で遷移しても変数値は保持される（遷移先でも同名の変数宣言が必要）
- **組み込み変数**: `stock` は宣言不要で常に利用可能

**変数の利用場所**:

| 場所 | 書式 | 例 |
|------|------|----|
| メッセージ内 | `<変数名>` | `"残りHP: <HP>"` |
| 条件式 | 変数名そのまま | `"HP > 0 && gameCount >= 10"` |
| 計算式 | 変数名そのまま | `"stock * 1000 * var1"` |
| 報酬の value | 変数名そのまま | `"value": "stock * 2"` |

---

### イベントシステム（event）

条件に応じて変数操作・メッセージ表示・報酬付与・スロット遷移などを実行できます。

**2つの配置場所**:
- **トップレベル `event`**: 毎スピン開始時に評価される（グローバルイベント）
- **パターン内 `event`**: そのパターンに当選した時のみ評価される

```jsonc
{
  "condition": "gameCount >= 999",                       // 条件式（"1"で常時実行）
  "varCalc": "atStock = atStock + 1; gameCount = 0",    // 変数操作（; で複数実行）
  "message": "&6天井到達！AT確定！",                      // 表示メッセージ
  "rewards": [ ... ],                                    // 報酬（パターンと同形式）
  "nextSlotOnWin": "dragon_rush/at",                     // スロット遷移
  "eventSound": {                                        // 効果音
    "type": "minecraft:ui.toast.challenge_complete",
    "volume": 1.0, "pitch": 1.0, "radius": 20
  }
}
```

#### 条件式（condition）の書き方

| 演算子 | 説明 | 例 |
|--------|------|----|
| `+` `-` `*` `/` `%` | 四則演算・剰余 | `stock % 50 == 0` |
| `<` `>` `<=` `>=` | 比較 | `HP > 0` |
| `==` `!=` | 等値・不等値 | `highMode == 1` |
| `&&` `\|\|` `!` | 論理AND・OR・NOT | `czStock >= 1 && atStock == 0` |

- `"1"` で常に true（毎回実行される）
- 変数名は自動的に現在値に置換されてから評価される

#### 変数操作（varCalc）の書き方

```
"varCalc": "HP = HP - 2; gameCount = gameCount + 1; stock = 0"
```

- セミコロン `;` で複数の代入を区切る
- 右辺には変数式が使える
- `stock` も操作可能

---

### サウンド設定

4種類の基本サウンドを設定できます：

| キー | タイミング |
|------|-----------|
| `startSound` | スロット開始時 |
| `rotatingSound` | 回転中（繰り返し再生）。未設定時は額縁の設置音 |
| `reelStopSound` | リール停止時（各リールごとに1回） |
| `endLoseSound` | ハズレ確定時 |

```jsonc
{
  "type": "minecraft:block.note_block.pling",  // サウンドID
  "volume": 1.0,    // 音量（デフォルト: 0.5、rotatingSound のみ 0.3）
  "pitch": 1.0,     // 音程 0.5〜2.0（デフォルト: 1.0）
  "radius": 10.0    // 範囲（ブロック数）/ -1=回した人のみ / -2=全プレイヤー
}
```

スロット設定ファイルで `defaultSoundSettings` を省略した場合、コード内のデフォルト値（volume: 0.5, pitch: 1.0, radius: 10.0）が適用されます。

---

### パーティクル設定

```jsonc
{
  "point": "button",         // "button"（ボタン位置）or "frame"（各リール位置）
  "particle": "END_ROD",     // Minecraft パーティクル名
  "count": 10,               // パーティクル数
  "speed": 0.1,              // 拡散速度
  "offset": [0.2, 0.5, 0.2], // [横, 上, 正面] の拡散範囲
  "color": [1.0, 0.0, 0.0]   // RGB 0.0〜1.0（DUST パーティクル専用）
}
```

- **`defaultParticleSettings`**（トップレベル）: 回転中に表示
- **`particleSettings`**（パターン内）: 当選時に表示

---

### カスタムアイテム（itemConfigs）

エンチャントやカスタム名付きのアイテムをスロットのリールや報酬に使えます。

#### 1. Base64 文字列を取得

カスタムアイテムを手に持って以下を実行：

```
/slot itemstack
```

Base64 エンコードされた文字列が**サーバーコンソール**に出力されます。

#### 2. アイテム設定ファイルに登録

`plugins/slotMachinePlugin/itemConfigs/` に JSON ファイルを作成：

```jsonc
{
  "my_sword": "rO0ABXNyABNvcm...(Base64文字列)",
  "rare_armor": "rO0ABXNyABNvcm...(Base64文字列)"
}
```

#### 3. スロット設定で参照

```jsonc
"items": ["my_sword", "my_sword", "my_sword"],
"rewards": [
  { "type": "item", "value": "my_sword", "quantity": 1 }
]
```

---

### スロット遷移（nextSlotOnWin）

`nextSlotOnWin` を使うと、当選時にマシンのスロット設定を切り替えられます。これにより、複数のモードを持つスロット台を設計できます。

**例: AT機の設計**

```
normal.jsonc（通常時）
  ├─ CZトリガー当選 → nextSlotOnWin: "dragon_rush/cz"
  └─ 天井到達（999G）→ nextSlotOnWin: "dragon_rush/at"

cz.jsonc（チャレンジゾーン）
  ├─ CZ成功 → nextSlotOnWin: "dragon_rush/at"
  └─ CZ失敗 → nextSlotOnWin: "dragon_rush/normal"

at.jsonc（ボーナスタイム）
  ├─ 継続 → nextSlotOnWin: "dragon_rush/at"（自身へ遷移）
  ├─ 上位モード → nextSlotOnWin: "dragon_rush/rush"
  └─ 終了 → nextSlotOnWin: "dragon_rush/normal"

rush.jsonc（上位ボーナス）
  └─ 終了 → nextSlotOnWin: "dragon_rush/at"
```

**変数の引き継ぎ**: 遷移元で持っていた変数値はそのまま引き継がれます。ただし遷移先でも同名の `variables` を宣言してください（`initialValue` は初回のみ使われるため、遷移時は無視されます）。

---

## 設計例

### 例1: シンプルなスロット

```jsonc
{
  "reels": 3,
  "shuffleTime": 3.0,
  "shuffleSpeed": 1.0,
  "spinSpeed": 1.0,
  "spinCost": 100,
  "loseStockOperation": "ADD",
  "loseStockValue": 5,
  "loseMessage": "&7ハズレ...",
  "patterns": [
    {
      "probability": 10.0,
      "items": ["DIAMOND", "DIAMOND", "DIAMOND"],
      "rewards": [{ "type": "money", "value": "1000" }],
      "winMessage": "&b&lダイヤモンド揃い! &e+1000枚"
    },
    {
      "probability": 30.0,
      "items": ["GOLD_INGOT", "GOLD_INGOT", "GOLD_INGOT"],
      "rewards": [{ "type": "money", "value": "200" }],
      "winMessage": "&6金揃い! &e+200枚"
    }
  ]
}
```

### 例2: 変数を使った天井付きスロット

```jsonc
{
  "reels": 3,
  "shuffleTime": 2.5,
  "shuffleSpeed": 1.5,
  "spinSpeed": 1.0,
  "spinCost": 3,
  "loseStockOperation": "ADD",
  "loseStockValue": 1,
  "loseMessage": "&7通常 &f<gameCount>&7G",
  "variables": [
    { "varName": "gameCount", "initialValue": 0 }
  ],
  "event": [
    {
      "condition": "1",
      "varCalc": "gameCount = gameCount + 1"
    },
    {
      "condition": "gameCount >= 500",
      "varCalc": "gameCount = 0",
      "message": "&6&l天井到達！大当たり確定！",
      "nextSlotOnWin": "my_slot/bonus"
    }
  ],
  "patterns": [
    {
      "probability": 5.0,
      "items": ["NETHER_STAR", "NETHER_STAR", "NETHER_STAR"],
      "rewards": [{ "type": "money", "value": "500" }],
      "winMessage": "&e&l大当たり! +500枚",
      "nextSlotOnWin": "my_slot/bonus",
      "event": [
        {
          "condition": "1",
          "varCalc": "gameCount = 0"
        }
      ]
    }
  ]
}
```

---

## メッセージの装飾

メッセージ内では以下が利用できます：

| 記法 | 説明 |
|------|------|
| `&0`〜`&f` | カラーコード |
| `&l` | 太字 |
| `&n` | 下線 |
| `&o` | 斜体 |
| `&m` | 取り消し線 |
| `&r` | リセット |
| `\n` | 改行 |
| `<変数名>` | 変数の現在値（全メッセージで利用可） |

**特殊プレースホルダ（利用可能な場所が限定されます）：**

| 記法 | 利用可能な場所 |
|------|---------------|
| `<profit>` | `winMessage`, `broadcastSettings.message` |
| `<playerName>` | `broadcastSettings.message` のみ |
| `<slotName>` | `broadcastSettings.message` のみ |

---

## 依存関係

- [Paper API](https://papermc.io/) 1.21+
- [Vault](https://github.com/MilkBowl/VaultAPI) 1.7

## ライセンス

MIT

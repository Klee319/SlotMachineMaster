# SlotMachineMaster

Minecraft Paper プラグイン - カスタマイズ可能なスロットマシンシステム

## 動作環境

- Paper 1.21+
- Java 16+
- Vault (経済機能を使う場合)

## インストール

1. `slotMachinePlugin-1.0-SNAPSHOT.jar` を `plugins/` フォルダに配置
2. サーバーを起動
3. `plugins/slotMachinePlugin/` に設定フォルダとテンプレートが自動生成されます

## フォルダ構成

```
plugins/slotMachinePlugin/
├── slotConfigs/          # スロット設定 (.json / .jsonc)
│   └── example.jsonc     # テンプレート
├── itemConfigs/          # カスタムアイテム定義 (.json)
│   └── example.json      # テンプレート
└── machines.json         # マシン登録データ（自動生成）
```

## コマンド

| コマンド | 説明 |
|---------|------|
| `/slot help` | コマンド一覧を表示 |
| `/slot list` | 登録されたスロット一覧を表示 |
| `/slot set <machineId> <configPath>` | ボタンにスロットをセット |
| `/slot delete <machineId>` | スロットを削除 |
| `/slot reload` | 設定をリロード |
| `/slot analytics <topSlot\|topUser> [days]` | 指定期間のランキング表示 |
| `/slot debug <machineId> <count>` | 還元率シミュレーション（非同期実行） |
| `/slot itemstack` | 手持ちアイテムのBase64をコンソール出力 |

## 基本的な使い方

### 1. スロット設定ファイルを作成

`plugins/slotMachinePlugin/slotConfigs/myslot.jsonc` を作成（example.jsonc を参考に）

### 2. ゲーム内でスロットを設置

1. 壁にボタンを設置（天井・床は不可）
2. ボタンの上に額縁を3つ並べる（リール数分）
3. ボタンを見ながらコマンド実行:
   ```
   /slot set myMachine myslot
   ```

### 3. プレイ

ボタンを押すとスロットが回転します。

## 設定ファイル (JSONC形式)

```jsonc
{
  // 基本設定
  "reels": 3,                    // リール数
  "shuffleTime": 2.0,            // シャッフル時間(秒)
  "spinCost": 100,               // 1回のコスト(お金)

  // 負けた時の設定
  "loseStockOperation": "add",   // "add" / "set" / "sub"
  "loseStockValue": 10,
  "loseMessage": "&c残念！",

  // パターン（当選役）
  "patterns": [
    {
      "probability": "0.01",           // 当選確率
      "items": ["DIAMOND", "DIAMOND", "DIAMOND"],
      "rewards": [
        { "type": "money", "value": "stock*10" },
        { "type": "item", "value": "DIAMOND", "quantity": "5" }
      ],
      "winMessage": "&6ジャックポット！"
    }
  ]
}
```

### 報酬の書き方

| 種類 | 例 | 説明 |
|------|-----|------|
| 固定金額 | `"value": "1000"` | 1000円 |
| STOCK連動 | `"value": "stock*10"` | STOCKの10倍 |
| アイテム | `"type": "item", "value": "DIAMOND", "quantity": "5"` | ダイヤモンド5個 |
| カスタムアイテム | `"value": "myCustomSword"` | itemConfigsで定義したアイテム |

### カスタムアイテムの登録

1. ゲーム内でアイテムを手に持つ
2. `/slot itemstack` を実行
3. コンソールに出力されたBase64文字列をコピー
4. `itemConfigs/items.json` に追加:
   ```json
   {
     "myCustomSword": "Base64文字列..."
   }
   ```

## デバッグ（還元率計算）

```
/slot debug myMachine 1000000
```

非同期で実行され、10%ごとに進捗が表示されます。

## ライセンス

MIT License

package com.github.klee.slotMachinePlugin;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SlotConfig: JSON設定マッピングクラス (最終リファクタリング後)
 * <p>
 * ・デフォルトサウンド設定(defultSoundSettings)は SoundParam をオブジェクト化
 * ・パターン内サウンド (patternSound) もオブジェクト化
 * ・ブロードキャストサウンド (broadcastSound) もオブジェクト化
 * ・EventDefinition のサウンド (eventSound) もオブジェクト化
 * ・PatternConfig の reward を "List<Reward>" に変更して複数指定可能
 */
public class SlotConfig {

    private int reels;
    private double shuffleTime;
    private double spinCost;
    private double shuffleSpeed;
    private double spinSpeed;
    private String loseStockOperation;
    private int loseStockValue;
    private String loseMessage;
    private List<ParticleSetting> defaultParticleSettings;
    private DefaultSoundSettings defaultSoundSettings;
    private ItemCost itemCost;
    private List<PatternConfig> patterns;

    private List<VariableDefinition> variables;
    private List<EventDefinition> event;

    public List<ParticleSetting> getDefaultParticleSettings() {
        // null回避
        return (defaultParticleSettings != null)
                ? defaultParticleSettings
                : java.util.Collections.emptyList();
    }

    // --------------------------------------------------
    // Getters
    // --------------------------------------------------

    public ItemCost getItemCost() {
        return itemCost;
    }

    public double getShuffleTime() {
        return shuffleTime;
    }

    public double getSpinCost() {
        return spinCost;
    }

    public double getShuffleSpeed() {
        return shuffleSpeed;
    }

    public double getSpinSpeed() {
        return spinSpeed;
    }

    public String getLoseStockOperation() {
        return loseStockOperation;
    }

    public int getLoseStockValue() {
        return loseStockValue;
    }

    public String getLoseMessage() {
        return loseMessage;
    }

    public DefaultSoundSettings getDefaultSoundSettings() {
        return defaultSoundSettings;
    }

    public List<PatternConfig> getPatterns() {
        return patterns;
    }

    public List<VariableDefinition> getVariables() {
        return variables;
    }

    public List<EventDefinition> getEvent() {
        return event;
    }

    //================================================
    // デフォルトサウンド設定
    //================================================
    public static class DefaultSoundSettings {
        private SoundParam startSound;
        private SoundParam rotatingSound;
        private SoundParam reelStopSound;
        private SoundParam endLoseSound;

        public SoundParam getStartSound() {
            return startSound;
        }

        public SoundParam getRotatingSound() {
            return rotatingSound;
        }

        public SoundParam getReelStopSound() {
            return reelStopSound;
        }

        public SoundParam getEndLoseSound() {
            return endLoseSound;
        }

        // サウンド1種類分
        public static class SoundParam {
            private String type;    // "minecraft:block.note_block.pling" etc
            private double volume;  // default=1.0
            private double pitch;   // default=1.0
            private double radius;  // default=2.0

            public String getType() {
                return type;
            }

            public double getVolume() {
                return volume;
            }

            public double getPitch() {
                return pitch;
            }

            public double getRadius() {
                return radius;
            }
        }
    }
    public static class ItemCost {
        private String name;   // itemConfigのキー or Bukkit Material名
        private int amount;    // 必要数

        public String getName() { return name; }
        public int getAmount() { return amount; }
    }

    //================================================
    // パターン定義
    //================================================
    public static class PatternConfig {
        private String probability;

        // パターンサウンド (一式)
        private PatternSoundParam patternSound;

        // items
        private List<String> items;

        // ★ 複数リワードに対応
        private List<Reward> rewards;

        private String stockOperation;
        private int stockValue;
        private String nextSlotOnWin;

        private BroadcastSettings broadcastSettings;

        // pattern内でも eventを利用可
        private List<EventDefinition> event;

        // パーティクル
        private List<ParticleSetting> particleSettings;
        private String winMessage;

        public String getWinMessage() {
            return winMessage;
        }

        public String getProbability() {
            return probability;
        }

        public PatternSoundParam getPatternSound() {
            return patternSound;
        }

        public List<String> getItems() {
            return items;
        }

        // ★ 変更: まとめて複数リワード
        public List<Reward> getRewards() {
            return (rewards != null) ? rewards : java.util.Collections.emptyList();
        }

        public String getStockOperation() {
            return stockOperation;
        }

        public int getStockValue() {
            return stockValue;
        }

        public String getNextSlotOnWin() {
            return nextSlotOnWin;
        }

        public BroadcastSettings getBroadcastSettings() {
            return broadcastSettings;
        }

        public List<EventDefinition> getEvent() {
            return event;
        }

        public List<ParticleSetting> getParticleSettings() {
            return particleSettings;
        }

        // パターンサウンド
        public static class PatternSoundParam {
            private String type;
            private double volume;
            private double pitch;
            private double radius;

            public String getType() {
                return type;
            }

            public double getVolume() {
                return volume;
            }

            public double getPitch() {
                return pitch;
            }

            public double getRadius() {
                return radius;
            }
        }

    }

    //================================================
    // ブロードキャスト設定
    //================================================
    public static class BroadcastSettings {

        private BroadcastSoundParam broadcastSound;

        // ブロードキャスト時のメッセージ
        private String message;

        public BroadcastSoundParam getBroadcastSound() {
            return broadcastSound;
        }

        public String getMessage() {
            return message;
        }

        public static class BroadcastSoundParam {
            private String type;
            private double volume;
            private double pitch;
            private double radius;

            public String getType() {
                return type;
            }

            public double getVolume() {
                return volume;
            }

            public double getPitch() {
                return pitch;
            }

            public double getRadius() {
                return radius;
            }
        }
    }

    //================================================
    // イベント(変数条件)
    //================================================
    public static class EventDefinition {
        private String condition;
        private String varCalc;
        private String message;
        private List<Reward> rewards;
        private String nextSlotOnWin;

        // イベント用サウンドオブジェクト
        private EventSoundParam eventSound;

        public String getCondition() {
            return condition;
        }

        public String getVarCalc() {
            return varCalc;
        }

        public String getMessage() {
            return message;
        }

        public List<Reward> getRewards() {
            return Objects.requireNonNullElse(rewards, Collections.emptyList());
        }

        public String getNextSlotOnWin() {
            return nextSlotOnWin;
        }

        public EventSoundParam getEventSound() {
            return eventSound;
        }

        public static class EventSoundParam {
            private String type;   // "minecraft:block.note_block.pling"
            private double volume; // 1.0
            private double pitch;  // 1.0
            private double radius; // 2.0

            public String getType() {
                return type;
            }

            public double getVolume() {
                return volume;
            }

            public double getPitch() {
                return pitch;
            }

            public double getRadius() {
                return radius;
            }
        }
    }

    //================================================
    // パーティクル設定
    //================================================
    public static class ParticleSetting {
        private String particle;
        private int count;
        private double speed;
        private double[] offset;
        private double[] color; // [r,g,b]
        private String point;

        public String getParticle() {
            return particle;
        }

        public int getCount() {
            return count;
        }

        public double getSpeed() {
            return speed;
        }

        public double[] getOffset() {
            return offset;
        }

        public double[] getColor() {
            return color;
        }

        public String getPoint() {
            return point;
        }
    }

    //================================================
    // 報酬
    //================================================
    public static class Reward {
        private String type;   // "money" or "item"
        private String value;  // 例: "1000" or "STOCK*2" or "DIAMOND"
        private String quantity;

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public String getQuantity() {
            return quantity;
        }
    }


    //================================================
    // 変数定義
    //================================================
    public static class VariableDefinition {
        private String varName;
        private double initialValue;

        public String getVarName() {
            return varName;
        }

        public double getInitialValue() {
            return initialValue;
        }
    }
}

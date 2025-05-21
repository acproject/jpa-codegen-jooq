from peft import LoraConfig


lora_config = LoraConfig(
    r=16,                                           # 秩
    lora_alpha=32,                                  # 缩放系数
    target_modules=["q_proj", "v_proj"],            # 目标模块（注意力层的Q， V矩阵）
    lora_dropout=0.1,
    bias="none",                                    # 不训练偏置项
    task_type="CAUSAL_LM",                          # 因果语言模型
    inference_mode=False,
    # lora_weight_path="./output/lora-alpaca-7b-v1.0/adapter_model.bin",
    # lora_weight_path="./output/lora-alpaca-7b-v1.0/adapter_model.bin",
)

from transformers import AutoModelForCausalLM, AutoTokenizer, TrainingArguments
from  peft import get_peft_model
import torch

# 加载模型和Tokenizer
model = AutoModelForCausalLM.from_pretrained(
    "microsoft/Phi-3-medium-4k-instruct",
    torch_dtype=torch.bfloat16)

AutoTokenizer.from_pretrained("microsoft/Phi-3-medium-4k-instruct")

# 添加LoRA适配器
model = get_peft_model(model, lora_config)
model.print_trainable_parameters() # 可以查看训练参数量（通常0.1%-1%）

# 配置训练参数
training_args = TrainingArguments(
    output_dir="output/lora-Phi-3-medium-4k-instruct",
    learning_rate=3e-4,
    num_train_epochs=5,
    per_device_train_batch_size=8,
    gradient_accumulation_steps=2,      # 显存不足时增大有效batch size
    fp16=True,                          # 混合精度训练
    optim="paged_adamw_32bit",
    logging_steps=50,
    save_strategy="steps",
    save_steps=100,
    save_total_limit=2,
    weight_decay=0.01,
)

import json
from datasets import Dataset

# 加载 JSON 数据
with open("examples/json/train_data.json", "r", encoding="utf-8") as f:
    dataset_json = json.load(f)

# 转换为 Hugging Face Dataset 格式
dataset = Dataset.from_list(dataset_json)

# 划分训练集和验证集（前700条训练，后300条验证）
train_dataset = dataset.select(range(700))     # 前700条作为训练集
eval_dataset = dataset.select(range(700, 1000)) # 后300条作为验证

def preprocess_function(examples):
    instructions = examples["instruction"]
    steps_dsls = [step["dsl"] for step in examples["steps"]]

    # 构建输入文本，这里是一个简单拼接示例，你可以根据实际需要设计更复杂的 prompt 模板
    inputs = [f"Instruction: {inst} DSL: {dsl}" for inst, dsl in zip(instructions, steps_dsls)]

    return tokenizer(inputs, padding="max_length", truncation=True, max_length=512)

# 对数据集进行映射处理
tokenized_train_dataset = train_dataset.map(preprocess_function, batched=True)
tokenized_eval_dataset = eval_dataset.map(preprocess_function, batched=True)

from transformers import Trainer

# 初始化Tokenizer
tokenizer = AutoTokenizer.from_pretrained("microsoft/Phi-3-medium-4k-instruct")

# 开始训练
trainer = Trainer(
    model=model,
    args=training_args,
    train_dataset=train_dataset,
    eval_dataset=eval_dataset,
    tokenizer=tokenizer,
)


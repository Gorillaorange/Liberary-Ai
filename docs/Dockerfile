# Dockerfile（需在可联网环境构建）
FROM nvcr.io/nvidia/pytorch:23.12-py3 as builder

# 阶段1：依赖安装
RUN apt-get update && apt-get install -y \
    python3.8 python3-pip \
    && rm -rf /var/lib/apt/lists/*

# 配置Python3.8为默认版本
RUN update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.8 1

# 安装核心库（提前下载离线包）
COPY ./offline_packages /tmp/offline_packages
RUN pip3 install --no-index --find-links=/tmp/offline_packages \
    torch==2.0.1+cu118 \
    transformers==4.38.2 \
    accelerate==0.27.2 \
    bitsandbytes==0.42.0 \
    unsloth==2024.1

# 阶段2：生成精简镜像
FROM nvidia/cuda:11.8.0-base-ubuntu20.04
COPY --from=builder /usr/local/lib/python3.8 /usr/local/lib/python3.8
COPY --from=builder /usr/local/bin /usr/local/bin

# 验证环境
RUN nvidia-smi && python3 -c "import torch; print(torch.__version__)"
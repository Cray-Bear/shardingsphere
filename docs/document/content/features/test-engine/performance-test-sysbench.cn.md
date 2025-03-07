+++
pre = "<b>3.9.5. </b>"
title = "性能测试(sysbench)"
weight = 5
+++

## 环境

#### 推荐硬件环境

```
CPU: 32 Cores
RAM: 128 GB
NIC: 10Gb Ethernet
```

至少需要5台机器：

```
Jenkins * 1: ${host-jenkins}
Sysbench * 1: ${host-sysbench}
ShardingSphere-Proxy * 1: ${host-proxy}
MySQL Server * 2: ${host-mysql-1}, ${host-mysql-2}
```

可以适当降低Jenkins和Sysbench机器的硬件标准

#### 软件环境

```
Jenins: 最新版本
Sysbench: 1.0.20
ShardingSphere-Proxy: master分支代码打包
MySQL Server: 5.7.28
```

## 测试方案

根据以上的硬件环境，配置参数如下，参数应根据硬件环境改变而调整

#### ShardingSphere-Proxy配置

```
Proxy运行在${host-proxy}机器
版本包括：Master分支版本、4.1.1版本、3.0.0版本
场景包括：config-sharding、config-replica-query、config-sharding-replica-query、config-encrypt
配置文件详细内容：见附录1
```

#### MySQL Server配置

两个MySQL实例分别运行在${host-mysql-1}和${host-mysql-2}机器
```
需要提前在两个实例上创建sbtest数据库
设置参数max_prepared_stmt_count = 500000
设置参数max_connections = 2000
```

#### Jenkins配置

创建6个Jenkins任务，每个任务依次调用下一个任务：（运行在${host-jenkins}机器）

```
1. sysbench_install: 拉取最新代码，打包Proxy压缩包
```

以下任务通过Jenkins slave运行在单独的Sysbench发压机器：（运行在${host-sysbench}机器）
```
2. sysbench_sharding: 
   a. 远程部署各版本Proxy的分片场景
   b. 执行Sysbench命令压测Proxy
   c. 执行Sysbench命令压测MySQL Server
   d. 保存Sysbench压测结果
   e. 使用画图脚本生成性能曲线和表格（画图脚本见附录2）
3. sysbench_master_slave:
   a. 远程部署各版本Proxy的读写分离场景
   b. 执行Sysbench命令压测Proxy
   c. 执行Sysbench命令压测MySQL Server
   d. 保存Sysbench压测结果
   e. 使用画图脚本生成性能曲线和表格
4. sysbench_sharding_master_slave:
   a. 远程部署各版本Proxy的分片+读写分离场景
   b. 执行Sysbench命令压测Proxy
   c. 执行Sysbench命令压测MySQL Server
   d. 保存Sysbench压测结果
   e. 使用画图脚本生成性能曲线和表格
5. sysbench_encrypt:
   a. 远程部署各版本Proxy的加密场景
   b. 执行Sysbench命令压测Proxy
   c. 执行Sysbench命令压测MySQL Server
   d. 保存Sysbench压测结果
   e. 使用画图脚本生成性能曲线和表格
6. sysbench_result_aggregation:
   a. 重新对所有任务的压测结果执行画图脚本
      python3 plot_graph.py sharding
      python3 plot_graph.py ms
      python3 plot_graph.py sharding_ms
      python3 plot_graph.py encrypt
   b. 使用Jenkins的Publish HTML reports插件将所有图片整合到一个HTML页面中
```

## 测试过程

以sysbench_sharding为例（其他场景类似）

#### 进入sysbench压测结果目录

```bash
cd /home/jenkins/sysbench_res/sharding
```

#### 创建本次构建的文件夹

```bash
mkdir $BUILD_NUMBER
```

#### 取最后14次构建，保存到隐藏文件中

```bash
ls -v | tail -n14 > .build_number.txt
```

#### 部署及压测

步骤1 执行远程部署脚本，部署Proxy到${host-proxy}

./deploy_sharding.sh

```bash
#!/bin/sh

rm -fr apache-shardingsphere-*-shardingsphere-proxy-bin
tar zxvf apache-shardingsphere-*-shardingsphere-proxy-bin.tar.gz

sh stop_proxy.sh

cp -f prepared_conf/mysql-connector-java-5.1.47.jar apache-shardingsphere-*-shardingsphere-proxy-bin/lib
cp -f prepared_conf/start.sh apache-shardingsphere-*-shardingsphere-proxy-bin/bin
cp -f prepared_conf/config-sharding.yaml prepared_conf/server.yaml apache-shardingsphere-*-shardingsphere-proxy-bin/conf

./apache-shardingsphere-*-shardingsphere-proxy-bin/bin/start.sh

sleep 30
```

步骤2 执行sysbench脚本

```bash
# master

cd /home/jenkins/sysbench_res/sharding
cd $BUILD_NUMBER

sysbench oltp_read_only --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=10 --time=3600 --threads=10 --max-requests=0 --percentile=99 --mysql-ignore-errors="all" --rand-type=uniform --range_selects=off --auto_inc=off cleanup
sysbench oltp_read_only --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=10 --time=3600 --threads=10 --max-requests=0 --percentile=99 --mysql-ignore-errors="all" --rand-type=uniform --range_selects=off --auto_inc=off prepare

sysbench oltp_read_only        --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run
sysbench oltp_read_only        --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run | tee oltp_read_only.master.txt
sysbench oltp_point_select     --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run | tee oltp_point_select.master.txt
sysbench oltp_read_write       --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run | tee oltp_read_write.master.txt
sysbench oltp_write_only       --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run | tee oltp_write_only.master.txt
sysbench oltp_update_index     --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run | tee oltp_update_index.master.txt
sysbench oltp_update_non_index --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run | tee oltp_update_non_index.master.txt
sysbench oltp_delete           --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=30  --time=180 --threads=256 --max-requests=0 --percentile=99  --mysql-ignore-errors="all" --range_selects=off --rand-type=uniform --auto_inc=off run | tee oltp_delete.master.txt

sysbench oltp_read_only --mysql-host=${host-proxy} --mysql-port=3307 --mysql-user=root --mysql-password='root' --mysql-db=sbtest --tables=10 --table-size=1000000 --report-interval=10 --time=3600 --threads=10 --max-requests=0 --percentile=99 --mysql-ignore-errors="all" --rand-type=uniform --range_selects=off --auto_inc=off cleanup
```

4.1.1、3.0.0、直连MySQL这三个场景，重复上面步骤1和步骤2

#### 执行停止Proxy脚本

./stop_proxy.sh

```bash
#!/bin/sh

./3.0.0_sharding-proxy/bin/stop.sh 
./4.1.1_apache-shardingsphere-4.1.1-sharding-proxy-bin/bin/stop.sh
./apache-shardingsphere-*-shardingsphere-proxy-bin/bin/stop.sh
```

#### 生成压测曲线图片

```bash
# Generate graph

cd /home/jenkins/sysbench_res/
python3 plot_graph.py sharding
```

#### 利用Jenkins的 Publish HTML reports插件 将图片发布到页面里

```
HTML directory to archive: /home/jenkins/sysbench_res/graph/
Index page[s]: 01_sharding.html
Report title: HTML Report
```

## sysbench测试用例分析

#### oltp_point_select

```
Prepare Statement (ID = 1): SELECT c FROM sbtest1 WHERE id=?
Execute Statement: ID = 1
```

#### oltp_read_only

```
Prepare Statement (ID = 1): 'COMMIT'
Prepare Statement (ID = 2): SELECT c FROM sbtest1 WHERE id=?

Statement: 'BEGIN'
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 1
```

#### oltp_write_only

```
Prepare Statement (ID = 1): 'COMMIT'
Prepare Statement (ID = 2): UPDATE sbtest1 SET k=k+1 WHERE id=?
Prepare Statement (ID = 3): UPDATE sbtest6 SET c=? WHERE id=?
Prepare Statement (ID = 4): DELETE FROM sbtest1 WHERE id=?
Prepare Statement (ID = 5): INSERT INTO sbtest1 (id, k, c, pad) VALUES (?, ?, ?, ?)

Statement: 'BEGIN'
Execute Statement: ID = 2
Execute Statement: ID = 3
Execute Statement: ID = 4
Execute Statement: ID = 5
Execute Statement: ID = 1
```

#### oltp_read_write

```
Prepare Statement (ID = 1): 'COMMIT'
Prepare Statement (ID = 2): SELECT c FROM sbtest1 WHERE id=?
Prepare Statement (ID = 3): UPDATE sbtest3 SET k=k+1 WHERE id=?
Prepare Statement (ID = 4): UPDATE sbtest10 SET c=? WHERE id=?
Prepare Statement (ID = 5): DELETE FROM sbtest8 WHERE id=?
Prepare Statement (ID = 6): INSERT INTO sbtest8 (id, k, c, pad) VALUES (?, ?, ?, ?)

Statement: 'BEGIN'
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 2
Execute Statement: ID = 3
Execute Statement: ID = 4
Execute Statement: ID = 5
Execute Statement: ID = 6
Execute Statement: ID = 1
```

#### oltp_update_index

```
Prepare Statement (ID = 1): UPDATE sbtest1 SET k=k+1 WHERE id=?

Execute Statement: ID = 1
```

#### oltp_update_non_index

```
Prepare Statement (ID = 1): UPDATE sbtest1 SET c=? WHERE id=?

Execute Statement: ID = 1
```

#### oltp_delete

```
Prepare Statement (ID = 1): DELETE FROM sbtest1 WHERE id=?

Execute Statement: ID = 1
```

## 附录1

#### Master branch version

server.yaml

```yaml
users:
  - root@%:root
  - sharding@:sharding

props:
  max-connections-size-per-query: 10
  executor-size: 128  # Infinite by default.
  proxy-frontend-flush-threshold: 128  # The default value is 128.
  proxy-opentracing-enabled: false
  proxy-hint-enabled: false
  sql-show: false
  check-table-metadata-enabled: false
  lock-wait-timeout-milliseconds: 50000 # The maximum time to wait for a lock
```

config-sharding.yaml

```yaml

schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256
    minPoolSize: 256
  ds_1:
    url: jdbc:mysql://${host-mysql-2}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256
    minPoolSize: 256

rules:
- !SHARDING
  tables:
    sbtest1:
      actualDataNodes: ds_${0..1}.sbtest1_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_1
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest2:
      actualDataNodes: ds_${0..1}.sbtest2_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_2
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest3:
      actualDataNodes: ds_${0..1}.sbtest3_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_3
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest4:
      actualDataNodes: ds_${0..1}.sbtest4_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_4
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest5:
      actualDataNodes: ds_${0..1}.sbtest5_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_5
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest6:
      actualDataNodes: ds_${0..1}.sbtest6_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_6
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest7:
      actualDataNodes: ds_${0..1}.sbtest7_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_7
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest8:
      actualDataNodes: ds_${0..1}.sbtest8_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_8
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest9:
      actualDataNodes: ds_${0..1}.sbtest9_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_9
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest10:
      actualDataNodes: ds_${0..1}.sbtest10_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_10
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake

  defaultDatabaseStrategy:
    standard:
      shardingColumn: id
      shardingAlgorithmName: database_inline

  shardingAlgorithms:
    database_inline:
      type: INLINE
      props:
        algorithm-expression: ds_${id % 2}
    table_inline_1:
      type: INLINE
      props:
        algorithm-expression: sbtest1_${id % 100}
    table_inline_2:
      type: INLINE
      props:
        algorithm-expression: sbtest2_${id % 100}
    table_inline_3:
      type: INLINE
      props:
        algorithm-expression: sbtest3_${id % 100}
    table_inline_4:
      type: INLINE
      props:
        algorithm-expression: sbtest4_${id % 100}
    table_inline_5:
      type: INLINE
      props:
        algorithm-expression: sbtest5_${id % 100}
    table_inline_6:
      type: INLINE
      props:
        algorithm-expression: sbtest6_${id % 100}
    table_inline_7:
      type: INLINE
      props:
        algorithm-expression: sbtest7_${id % 100}
    table_inline_8:
      type: INLINE
      props:
        algorithm-expression: sbtest8_${id % 100}
    table_inline_9:
      type: INLINE
      props:
        algorithm-expression: sbtest9_${id % 100}
    table_inline_10:
      type: INLINE
      props:
        algorithm-expression: sbtest10_${id % 100}
  keyGenerators:
    snowflake:
      type: SNOWFLAKE
      props:
        worker-id: 123
```

config-replica-query.yaml

```yaml
schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 128
    minPoolSize: 128

rules:
- !READWRITE_SPLITTING
  dataSources:
    pr_ds:
      primaryDataSourceName: ds_0
      replicaDataSourceNames:
        - ds_0
        - ds_0
```

config-sharding-replica-query.yaml

```yaml
schemaName: sbtest

dataSources:
  primary_ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256
    minPoolSize: 256
  primary_ds_1:
    url: jdbc:mysql://${host-mysql-2}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256
    minPoolSize: 256

rules:
- !SHARDING
  tables:
    sbtest1:
      actualDataNodes: ds_${0..1}.sbtest1_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_1
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest2:
      actualDataNodes: ds_${0..1}.sbtest2_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_2
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest3:
      actualDataNodes: ds_${0..1}.sbtest3_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_3
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest4:
      actualDataNodes: ds_${0..1}.sbtest4_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_4
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest5:
      actualDataNodes: ds_${0..1}.sbtest5_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_5
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest6:
      actualDataNodes: ds_${0..1}.sbtest6_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_6
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest7:
      actualDataNodes: ds_${0..1}.sbtest7_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_7
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest8:
      actualDataNodes: ds_${0..1}.sbtest8_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_8
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest9:
      actualDataNodes: ds_${0..1}.sbtest9_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_9
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
    sbtest10:
      actualDataNodes: ds_${0..1}.sbtest10_${0..99}
      tableStrategy:
        standard:
          shardingColumn: id
          shardingAlgorithmName: table_inline_10
      keyGenerateStrategy:
        column: id
        keyGeneratorName: snowflake
   
  defaultDatabaseStrategy:
    standard:
      shardingColumn: id
      shardingAlgorithmName: database_inline

  shardingAlgorithms:
    database_inline:
      type: INLINE
      props:
        algorithm-expression: ds_${id % 2}
    table_inline_1:
      type: INLINE
      props:
        algorithm-expression: sbtest1_${id % 100}
    table_inline_2:
      type: INLINE
      props:
        algorithm-expression: sbtest2_${id % 100}
    table_inline_3:
      type: INLINE
      props:
        algorithm-expression: sbtest3_${id % 100}
    table_inline_4:
      type: INLINE
      props:
        algorithm-expression: sbtest4_${id % 100}
    table_inline_5:
      type: INLINE
      props:
        algorithm-expression: sbtest5_${id % 100}
    table_inline_6:
      type: INLINE
      props:
        algorithm-expression: sbtest6_${id % 100}
    table_inline_7:
      type: INLINE
      props:
        algorithm-expression: sbtest7_${id % 100}
    table_inline_8:
      type: INLINE
      props:
        algorithm-expression: sbtest8_${id % 100}
    table_inline_9:
      type: INLINE
      props:
        algorithm-expression: sbtest9_${id % 100}
    table_inline_10:
      type: INLINE
      props:
        algorithm-expression: sbtest10_${id % 100}
  keyGenerators:
    snowflake:
      type: SNOWFLAKE
      props:
        worker-id: 123

- !READWRITE_SPLITTING
  dataSources:
    ds_0:
      primaryDataSourceName: primary_ds_0
      replicaDataSourceNames:
        - primary_ds_0
        - primary_ds_0
    ds_1:
      name: ds_1
      primaryDataSourceName: primary_ds_1
      replicaDataSourceNames:
        - primary_ds_1
        - primary_ds_1
```

config-encrypt.yaml

```yaml
schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256
    minPoolSize: 256

rules:
- !ENCRYPT
  encryptors:
    md5_encryptor:
      type: MD5
  tables:
    sbtest1:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest2:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest3:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest4:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest5:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest6:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest7:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest8:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest9:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
    sbtest10:
      columns:
        pad:
          cipherColumn: pad
          encryptorName: md5_encryptor
```

#### 4.1.1 version

server.yaml

```yaml
authentication:
  users:
    root:
      password: root
    sharding:
      password: sharding
      authorizedSchemas: sharding_db

props:
  max.connections.size.per.query: 10
  acceptor.size: 256  # The default value is available processors count * 2.
  executor.size: 128  # Infinite by default.
  proxy.frontend.flush.threshold: 128  # The default value is 128.
    # LOCAL: Proxy will run with LOCAL transaction.
    # XA: Proxy will run with XA transaction.
    # BASE: Proxy will run with B.A.S.E transaction.
  proxy.transaction.type: LOCAL
  proxy.opentracing.enabled: false
  proxy.hint.enabled: false
  query.with.cipher.column: true
  sql.show: false
  allow.range.query.with.inline.sharding: false
```

config-sharding.yaml

```yaml
schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256
  ds_1:
    url: jdbc:mysql://${host-mysql-2}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256

shardingRule:
  tables:
    sbtest1:
      actualDataNodes: ds_${0..1}.sbtest1_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest1_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest2:
      actualDataNodes: ds_${0..1}.sbtest2_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest2_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest3:
      actualDataNodes: ds_${0..1}.sbtest3_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest3_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest4:
      actualDataNodes: ds_${0..1}.sbtest4_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest4_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest5:
      actualDataNodes: ds_${0..1}.sbtest5_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest5_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest6:
      actualDataNodes: ds_${0..1}.sbtest6_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest6_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest7:
      actualDataNodes: ds_${0..1}.sbtest7_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest7_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest8:
      actualDataNodes: ds_${0..1}.sbtest8_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest8_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest9:
      actualDataNodes: ds_${0..1}.sbtest9_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest9_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest10:
      actualDataNodes: ds_${0..1}.sbtest10_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest10_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id

  defaultDatabaseStrategy:
    inline:
      shardingColumn: id
      algorithmExpression: ds_${id % 2}
```

config-master_slave.yaml

```yaml
schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256

masterSlaveRule:
  name: ms_ds
  masterDataSourceName: ds_0
  slaveDataSourceNames:
    - ds_0
    - ds_0
```

config-sharding-master_slave.yaml

```yaml
schemaName: sbtest

dataSources:
  primary_ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256
  primary_ds_1:
    url: jdbc:mysql://${host-mysql-2}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256

shardingRule:
  tables:
    sbtest1:
      actualDataNodes: ds_${0..1}.sbtest1_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest1_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest2:
      actualDataNodes: ds_${0..1}.sbtest2_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest2_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest3:
      actualDataNodes: ds_${0..1}.sbtest3_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest3_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest4:
      actualDataNodes: ds_${0..1}.sbtest4_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest4_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest5:
      actualDataNodes: ds_${0..1}.sbtest5_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest5_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest6:
      actualDataNodes: ds_${0..1}.sbtest6_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest6_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest7:
      actualDataNodes: ds_${0..1}.sbtest7_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest7_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest8:
      actualDataNodes: ds_${0..1}.sbtest8_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest8_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest9:
      actualDataNodes: ds_${0..1}.sbtest9_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest9_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id
    sbtest10:
      actualDataNodes: ds_${0..1}.sbtest10_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest10_${id % 100}
      keyGenerator:
        type: SNOWFLAKE
        column: id

  defaultDatabaseStrategy:
    inline:
      shardingColumn: id
      algorithmExpression: ds_${id % 2}

  masterSlaveRules:
    ds_0:
      masterDataSourceName: primary_ds_0
      slaveDataSourceNames: [primary_ds_0, primary_ds_0]
      loadBalanceAlgorithmType: ROUND_ROBIN
    ds_1:
      masterDataSourceName: primary_ds_1
      slaveDataSourceNames: [primary_ds_1, primary_ds_1]
      loadBalanceAlgorithmType: ROUND_ROBIN
```

config-encrypt.yaml

```yaml
schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    connectionTimeoutMilliseconds: 30000
    idleTimeoutMilliseconds: 60000
    maxLifetimeMilliseconds: 1800000
    maxPoolSize: 256

encryptRule:
  encryptors:
    encryptor_md5:
      type: md5
  tables:
    sbtest1:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest2:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest3:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest4:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest5:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest6:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest7:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest8:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest9:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
    sbtest10:
      columns:
        pad:
          cipherColumn: pad
          encryptor: encryptor_md5
```

#### 3.0.0 version

server.yaml

```yaml
authentication:
  username: root
  password: root

props:
  max.connections.size.per.query: 10
  acceptor.size: 256  # The default value is available processors count * 2.
  executor.size: 128  # Infinite by default.
  proxy.frontend.flush.threshold: 128  # The default value is 128.
    # LOCAL: Proxy will run with LOCAL transaction.
    # XA: Proxy will run with XA transaction.
    # BASE: Proxy will run with B.A.S.E transaction.
  proxy.transaction.type: LOCAL
  proxy.opentracing.enabled: false
  sql.show: false
```

config-sharding.yaml

```yaml
schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    autoCommit: true
    connectionTimeout: 30000
    idleTimeout: 60000
    maxLifetime: 1800000
    maximumPoolSize: 256
  ds_1:
    url: jdbc:mysql://${host-mysql-2}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    autoCommit: true
    connectionTimeout: 30000
    idleTimeout: 60000
    maxLifetime: 1800000
    maximumPoolSize: 256

shardingRule:
  tables:
    sbtest1:
      actualDataNodes: ds_${0..1}.sbtest1_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest1_${id % 100}
    sbtest2:
      actualDataNodes: ds_${0..1}.sbtest2_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest2_${id % 100}
    sbtest3:
      actualDataNodes: ds_${0..1}.sbtest3_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest3_${id % 100}
    sbtest4:
      actualDataNodes: ds_${0..1}.sbtest4_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest4_${id % 100}
    sbtest5:
      actualDataNodes: ds_${0..1}.sbtest5_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest5_${id % 100}
    sbtest6:
      actualDataNodes: ds_${0..1}.sbtest6_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest6_${id % 100}
    sbtest7:
      actualDataNodes: ds_${0..1}.sbtest7_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest7_${id % 100}
    sbtest8:
      actualDataNodes: ds_${0..1}.sbtest8_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest8_${id % 100}
    sbtest9:
      actualDataNodes: ds_${0..1}.sbtest9_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest9_${id % 100}
    sbtest10:
      actualDataNodes: ds_${0..1}.sbtest10_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest10_${id % 100}

  defaultDatabaseStrategy:
    inline:
      shardingColumn: id
      algorithmExpression: ds_${id % 2}
```

config-master_slave.yaml

```yaml
schemaName: sbtest

dataSources:
  ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    autoCommit: true
    connectionTimeout: 30000
    idleTimeout: 60000
    maxLifetime: 1800000
    maximumPoolSize: 256

masterSlaveRule:
  name: ms_ds
  masterDataSourceName: ds_0
  slaveDataSourceNames:
    - ds_0
    - ds_0
```

config-sharding-master_slave.yaml

```yaml
schemaName: sbtest

dataSources:
  primary_ds_0:
    url: jdbc:mysql://${host-mysql-1}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    autoCommit: true
    connectionTimeout: 30000
    idleTimeout: 60000
    maxLifetime: 1800000
    maximumPoolSize: 256
  primary_ds_1:
    url: jdbc:mysql://${host-mysql-2}:3306/sbtest?serverTimezone=UTC&useSSL=false
    username: root
    password:
    autoCommit: true
    connectionTimeout: 30000
    idleTimeout: 60000
    maxLifetime: 1800000
    maximumPoolSize: 256

shardingRule:
  tables:
    sbtest1:
      actualDataNodes: ds_${0..1}.sbtest1_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest1_${id % 100}
    sbtest2:
      actualDataNodes: ds_${0..1}.sbtest2_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest2_${id % 100}
    sbtest3:
      actualDataNodes: ds_${0..1}.sbtest3_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest3_${id % 100}
    sbtest4:
      actualDataNodes: ds_${0..1}.sbtest4_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest4_${id % 100}
    sbtest5:
      actualDataNodes: ds_${0..1}.sbtest5_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest5_${id % 100}
    sbtest6:
      actualDataNodes: ds_${0..1}.sbtest6_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest6_${id % 100}
    sbtest7:
      actualDataNodes: ds_${0..1}.sbtest7_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest7_${id % 100}
    sbtest8:
      actualDataNodes: ds_${0..1}.sbtest8_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest8_${id % 100}
    sbtest9:
      actualDataNodes: ds_${0..1}.sbtest9_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest9_${id % 100}
    sbtest10:
      actualDataNodes: ds_${0..1}.sbtest10_${0..99}
      tableStrategy:
        inline:
          shardingColumn: id
          algorithmExpression: sbtest10_${id % 100}

  defaultDatabaseStrategy:
    inline:
      shardingColumn: id
      algorithmExpression: ds_${id % 2}

  masterSlaveRules:
    ds_0:
      masterDataSourceName: primary_ds_0
      slaveDataSourceNames: [primary_ds_0, primary_ds_0]
      loadBalanceAlgorithmType: ROUND_ROBIN
    ds_1:
      masterDataSourceName: primary_ds_1
      slaveDataSourceNames: [primary_ds_1, primary_ds_1]
      loadBalanceAlgorithmType: ROUND_ROBIN
```

config-encrypt.yaml

```
不支持
```

## 附录2

plot_graph.py

```python
import sys
import matplotlib.pyplot as plt
import numpy as np


def generate_graph(path, case_name):
    dataset = {
        'build_num': [],
        'master_version': [],
        'master_xa': [],
        '4.1.1_version': [],
        '3.0.0_version': [],
        'mysql_server': []
    }
    with open(path + '/.build_number.txt') as builds:
        for line in builds:
            dataset['build_num'].append(int(line))
    generate_data(path, case_name, dataset)
    print(dataset)
    fig, ax = plt.subplots()
    ax.grid(True)
    plt.title(case_name)

    data = [dataset['master_version'][-7:], dataset['master_xa'][-7:], dataset['4.1.1_version'][-7:], dataset['3.0.0_version'][-7:], dataset['mysql_server'][-7:]]
    columns = dataset['build_num'][-7:]
    rows = ['master', 'xa', '4.1.1', '3.0.0', 'mysql']
    rcolors = plt.cm.BuPu(np.full(len(rows), 0.1))
    ccolors = plt.cm.BuPu(np.full(len(columns), 0.1))
    the_table = plt.table(cellText=data, rowLabels=rows, colLabels=columns, rowColours=rcolors, colColours=ccolors,
                          loc='bottom', bbox=[0.0, -0.50, 1, .28])
    plt.subplots_adjust(left=0.15, bottom=0.3, right=0.98)

    plt.xticks(range(14))
    ax.set_xticklabels(dataset['build_num'])
    plt.plot(dataset['master_version'], 'o-', color='magenta', label='master_version')
    plt.plot(dataset['master_xa'], 'o-', color='darkviolet', label='master_xa')
    plt.plot(dataset['4.1.1_version'], 'r--', color='blue', label='4.1.1_version')
    plt.plot(dataset['3.0.0_version'], 'r--', color='orange', label='3.0.0_version')
    plt.plot(dataset['mysql_server'], 'r--', color='lime', label='mysql_server')
    plt.xlim()
    plt.legend()
    plt.xlabel('build_num')
    plt.ylabel('transactions per second')
    plt.savefig('graph/' + path + '/' + case_name)
    plt.show()


def generate_data(path, case_name, dataset):
    for build in dataset['build_num']:
        fill_dataset(build, case_name, dataset, path, 'master_version', '.master.txt')
        fill_dataset(build, case_name, dataset, path, 'master_xa', '.xa.txt')
        fill_dataset(build, case_name, dataset, path, '4.1.1_version', '.4_1_1.txt')
        fill_dataset(build, case_name, dataset, path, '3.0.0_version', '.3_0_0.txt')
        fill_dataset(build, case_name, dataset, path, 'mysql_server', '.mysql.txt')


def fill_dataset(build, case_name, dataset, path, version, suffix):
    try:
        with open(path + '/' + str(build) + '/' + case_name + suffix) as version_master:
            value = 0
            for line in version_master:
                if 'transactions:' in line:
                    items = line.split('(')
                    value = float(items[1][:-10])
            dataset[version].append(value)
    except FileNotFoundError:
        dataset[version].append(0)


if __name__ == '__main__':
    path = sys.argv[1]
    generate_graph(path, 'oltp_point_select')
    generate_graph(path, 'oltp_read_only')
    generate_graph(path, 'oltp_write_only')
    generate_graph(path, 'oltp_read_write')
    generate_graph(path, 'oltp_update_index')
    generate_graph(path, 'oltp_update_non_index')
    generate_graph(path, 'oltp_delete')
```

目前在 ShardingSphere 的 benchmark 项目 [shardingsphere-benchmark](https://github.com/apache/shardingsphere-benchmark) 中已经共享了 sysbench 的使用方式 : [sysbench 压测工具](https://github.com/apache/shardingsphere-benchmark/blob/master/sysbench/README_ZH.md)

![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/tkote/fn-event-router)

# Fn Event Router - OCI Functions の HTTPリクエストを効率的に処理するフレームワーク

## これは何？

Fn Java FDK で OCI Functions のリクエストを処理を書くとき、エントリーポイントは1つのメソッドです。
API Gateway 経由で OCI Functions に複数の HTTP メソッド/パスのリクエストを処理させようとすると、エントリーポイントのメソッドの中で HTTP メソッド/パスに応じた振り分け作業を行って、最終的にまた同じエントリーポイントからリターン値を戻さないといけません。あるいはリターン値ではなくリターンする前にコンテキストを更新するようなパターンもあります。必然的にコーディングが複雑になりやすく、可視性も悪くなり、バグの温床にもなります。
そうした課題を解決するべく、FDK を薄くラップして JAX-RS のようにアノテーションを使ってすっきりと処理を書けるようにするためのフレームワークです。

Client から送信されたリクエストは以下のような経路を辿ってハンドラで処理されます。

```text
Client -> API GW -> Functions -> (FDK) -> [Fn Event Router] -> [HTTP Method/Path に応じたハンドラ]
```

ハンドラは受け取ったリクエストを処理してレスポンスを返すことだけに集中できます。

```java
@FnBean
public class HelloFunction {

    @FnHttpEvent(method = "GET", path = ".*/hello", outputType = "text")
    public String hello(String input) {
        // ...
    }
```

こんな風にコーディングすることができます。

## はじめの一歩

ここでは一番簡単な Fn Event Router アプリケーションを FDK のボイラープレートから作成してみます。

**1. プロジェクトの作成**

Fn CLI を使って Java FDK プロジェクトを作成します。

```bash
$ fn init --name sandbox --runtime java
```

**2. pom.xml の編集**

現在 Fn Event Router のライブラリはGitHubのリポジトリに置かれているので、これを参照できるようにします。
pom.xmlの適当なところ（この例では一番最後）にリポジトリの設定を加えます。

```xml
    ...
    <repositories>
        <repository>
            <id>fn-event-router-github</id>
            <url>https://raw.github.com/tkote/fn-event-router/mvn-repo/</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
</project>
```

Fn Event Router の dependency を追加します。

```xml
    <dependencies>
        <dependency>
            <groupId>io.github.tkote</groupId>
            <artifactId>fn-event-router</artifactId>
            <version>1.0.1</version>
        </dependency>
        ...
```

Fn Event Router はクラス走査高速化のために jandex を使ったインデックスが必要となりますので、plugin を追加します。

```xml
            ...
            <plugin>
                <groupId>org.jboss.jandex</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
                <version>1.2.0</version>
                <executions>
                    <execution>
                        <id>make-index</id>
                        <goals>
                            <goal>jandex</goal>
                          </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
```

**3. HelloFunction.java の編集**

生成されたソースにアノテーションを付加します。

```java
package com.example.fn;

import io.github.tkote.fn.eventrouter.annotation.FnBean; // 追加
import io.github.tkote.fn.eventrouter.annotation.FnHttpEvent; // 追加

@FnBean // 追加
public class HelloFunction {

    @FnHttpEvent(method = "GET", path = ".*/hello", outputType = "text") // 追加
    public String handleRequest(String input) {
        String name = (input == null || input.isEmpty()) ? "world"  : input;

        System.out.println("Inside Java Hello World function"); 
        return "Hello, " + name + "!";
    }

}
```

GET メソッド且つ ".*/hello" 正規表現とマッチするパスで呼び出された HTTP リクエストは、このメソッドにルーティングされます。
返り値が String 且つ @FnHttpEvent アノテーションで `outputType = "text"` としているので、HTTP レスポンスの Content-Type は text/plain になります。

**4. HelloFunctionTest.java の編集**

Maven でテストできるように、Test クラスも修正します。API Gateway 経由のリクエストをシミュレートします。

```java
package com.example.fn;

import com.fnproject.fn.testing.*;
import org.junit.*;

import static org.junit.Assert.*;

import io.github.tkote.fn.eventrouter.EventRouter; // 追加

public class HelloFunctionTest {

    @Rule
    public final FnTestingRule testing = FnTestingRule.createDefault();

    @Test
    public void shouldReturnGreeting() {
        //testing.givenEvent().enqueue(); // コメントアウト
        //testing.thenRun(HelloFunction.class, "handleRequest"); // コメントアウト

        // 追加（ここから）
        testing
        .setConfig("FN_APP_NAME", "testapp")
        .setConfig("FN_FN_NAME", "testfunc")
        .setConfig("OCI_TRACING_ENABLED", "0")
        .setConfig("OCI_TRACE_COLLECTOR_URL", "")
        .givenEvent()
        .withHeader("Fn-Http-Method", "GET")
        .withHeader("Fn-Http-Request-Url", "/hello")
        .enqueue();
        testing.thenRun(EventRouter.class, "handleRequest");
        // 追加（ここまで）

        FnResult result = testing.getOnlyResult();
        assertEquals("Hello, world!", result.getBodyAsString());
    }

}
```


**5. func.yaml の編集**

エントリーポイントを Fn Event Router に変更します。

```yaml
schema_version: 20180708
name: sandbox
version: 0.0.1
runtime: java
build_image: fnproject/fn-java-fdk-build:jdk11-1.0.146
run_image: fnproject/fn-java-fdk:jre11-1.0.146
#cmd: com.example.fn.HelloFunction::handleRequest // ↓に変更
cmd: io.github.tkote.fn.eventrouter.EventRouter::handleRequest
```

**6. テスト**

以下のような出力になっていれば OK です。

```
$ mvn test
...
[INFO] --- maven-surefire-plugin:2.22.1:test (default-test) @ hello ---
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.fn.HelloFunctionTest
yyyy.mm.dd hh:mm:ss INFO {{hostname}} io.github.tkote.fn.eventrouter.EventRouter: Setup: App=testapp, Function=testfunc
yyyy.mm.dd hh:mm:ss INFO {{hostname}} io.github.tkote.fn.eventrouter.EventRouter: HTTP Request (START): method=GET, requestURL=/hello
yyyy.mm.dd hh:mm:ss INFO {{hostname}} io.github.tkote.fn.eventrouter.EventRouter: Matched handler: com.example.fn.HelloFunction#handleRequest
Inside Java Hello World function
yyyy.mm.dd hh:mm:ss INFO {{hostname}} io.github.tkote.fn.eventrouter.EventRouter: HTTP Request (END): method=GET, requestURL=/hello
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.967 s - in com.example.fn.HelloFunctionTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
...
```

**7. デプロイ**

OCI Functions にデプロイします (fnapp というアプリケーションが既にあるという前提)。

```text
$ fn deploy -app fnapp
```

まずは fn を使って Functions を呼び出してみます。

```text
$ fn invoke fnapp sandbox --output json
{
    "body": "",
    "headers": {
        "Content-Length": [
            "0"
        ],
        "Content-Type": [
            "application/octet-stream"
        ],
        "Date": [
            "Thu, 17 Mar 2022 07:24:13 GMT"
        ],
        "Fn-Call-Id": [
            "01FYBC6GNQ1BT0G68ZJ002GYN0"
        ],
        "Fn-Fdk-Runtime": [
            "java/OpenJDK 64-Bit Server VM 11.0.11"
        ],
        "Fn-Fdk-Version": [
            "fdk-java/1.0.146 (jvm=OpenJDK 64-Bit Server VM, jvmv=11.0.11)"
        ],
        "Fn-Http-Status": [
            "500"
        ],
        "Opc-Request-Id": [
            "/01FYBC6GCD000000000004T40V/01FYBC6GCD000000000004T40W"
        ]
    },
    "status_code": 200
}
```

`Fn-Http-Status` が 500 なので HTTP 呼び出しとしてはエラーです。出力もありません。
OCI のログを見てみると `java.lang.IllegalStateException: No handler was found - method=, path=` と出力されています。Fnction を直接呼び出したので、HTTPリクエストに必要な情報が付加されておらず、適切なハンドラが見つからなかったことがわかります。

では、API Gateway 経由で Functions を呼び出してみましょう (ここでは OCI API Gateway の設定手順は省略します)。

```text
$ curl https://xxxxxxx.apigateway.us-ashburn-1.oci.customer-oci.com/sandbox/hello
Hello, world!
```

OCI のログを見てみると、HTTP メソッドとパスを評価してマッチするハンドラを呼び出しているのが分かります。

```text
Received function invocation request
0b4a1920a212 INFO io.github.tkote.fn.eventrouter.EventRouter: Setup: App=fnapp, Function=sandbox
0b4a1920a212 INFO io.github.tkote.fn.eventrouter.EventRouter: HTTP Request (START): method=GET, requestURL=/sandbox/hello
0b4a1920a212 INFO io.github.tkote.fn.eventrouter.EventRouter: Matched handler: io.github.tkote.fn.eventrouter.HelloFunction#handleRequest
Inside Java Hello World function
0b4a1920a212 INFO io.github.tkote.fn.eventrouter.EventRouter: HTTP Request (END): method=GET, requestURL=/sandbox/hello
Served function invocation request in 10.916 seconds
```

## しくみ

以下のアノテーション (パッケージ io.github.tkote.fn.eventrouter.annotation) を使います。


| アノテーション    | 設定場所     | 働き |
|------------------|-------------|---------------------------------------------------|
|FnBean            | クラス       | Fn HTTP Handler がマネージするクラスであることを示す |
|FnInit            | メソッド     | 起動後最初のリクエストがハンドラに渡される前に呼び出される |
|FnInject          | メンバー変数 | FnBean のインスタンスがインジェクトされる |
|FnHttpEvent       | メソッド     | 個々の HTTP メソッド/パスに応じたハンドラを設定する |

少なくとも 1つ以上の @FnHttpEvent でアノテートされたメソッドを持った 1つ以上の @FnBean でアノテートされたクラスが必要です。


### @FnBean 

Fn HTTP Handler がライフサイクルを管理するクラス (FnBeanと呼ぶ) であることを示します。ユーザが new する必要はありません。
FnBean は Fn HTTP Handler の起動時にそのインスタンスが生成され、シングルトンとして管理されます。

### @FnInit

@FnBean のついたクラスの中でのみ有効。メソッドに付与します。
FDK の @FnConfiguration メソッドが呼び出されるタイミングでこのメソッドが呼び出されます。
後述する通り、このメソッドから @FnInject メンバ変数を呼び出す際は注意が必要です。

**メソッドのパラメータと返り値**  

| 種別       | クラス                                   | 説明       |
|-----------|------------------------------------------|------------|
| パラメータ | com.fnproject.fn.api.<br/>RuntimeContext | 省略可     |
| 返り値     | void                                     |          |


### @FnInject

@FnBean のついたクラスの中でのみ有効。メンバー変数に付与します。
インジェクト処理は、＠FnInit メソッドが呼び出される前に実施されるので、＠FnInit メソッド内から @FnInject メンバ変数にアクセスすることはできますが、その対象となっているインスタンスの ＠FnInit メソッドが既に呼び出されている保証はありません。

### @FnHttpEvent

@FnBean のついたクラスの中でのみ有効。メソッドに付与する。
メソッドには任意の数のパラメータを設定することができます。

**@FnHttpEvent のパラメータ**

| パラメータ  | 説明                                                                                              |
|------------|---------------------------------------------------------------------------------------------------|
| method     | このメソッドが受け取るHTTPリクエストのメソッド、"ANY"を指定すると全てのメソッドが対象となる               |
| path       | このメソッドが受け取るHTTPリクエストのパス、正規表現で指定する                                          |
| outputType | このメソッドが String 型を返すときの Content-Type、"json"(デフォルト) もしくは "text" を指定する        |


**メソッドのパラメータと返り値**   

| 種別       | クラス                                                  | 説明                                                    |
|-----------|----------------------------------------------------------|---------------------------------------------------------|
| パラメータ | com.fnproject.fn.api.<br/>InputEvent                     |リクエストを受け取る Fn 純正クラス                         |
| パラメータ | String, byte                                             |リクエスト・ボディ                                        |
| パラメータ | 任意のクラス                                              |リクエスト・ボディ (Jsonをマッピング)                       |
| パラメータ | com.fnproject.fn.api.httpgateway.<br/>HTTPGatewayContext | HTTP リクエストの補足情報(ヘッダ等)                       |
| パラメータ | com.fnproject.fn.api.tracing.<br/>TracingContext         | トレーシングに関するコンテキスト                           |
| 返り値    | void                                                     | レスポンス・ボディ無し                                    |
| 返り値    | com.fnproject.fn.api.<br/>OutputEvent                     | レスポンスを返す Fn 純正クラス                            |
| 返り値    | io.github.tkote.fn.eventrouter.<br/>HttpResponse          | HTTPステータスコードを併せて返す場合                       |
| 返り値    | String                                                    | @FnHttpEvent の outputType パラメータ で Content-Type指定 |
| 返り値    | 任意のクラス                                               | Jsonにマッピング                                         |


### 補助クラス

#### HttpEventHelper (io.github.tkote.fn.eventrouter.HttpEventHelper)

com.fnproject.fn.api.InputEvent からリクエストの取り出しやレスポンスから com.fnproject.fn.api.OutputEvent の作成を行うヘルパークラスです。 

| メソッド                                                               | 説明                                                             |
|------------------------------------------------------------------------|------------------------------------------------------------------|
| static OutputEvent createJsonOutputEvent(Object obj)                   | 任意のオブジェクトから Json型の OutputEvent を作成する               |
| static OutputEvent createTextOutputEvent(String s)                     | テキスト型のレスポンスを返す OutputEvent を作成する                  |
| static &lt;T&gt; T getInputBody(InputEvent inputEvent, Class<T> clazz) | InputEvent からリクエストを指定したクラスのオブジェクトとして取り出す |
| static String getInputBodyAsString(InputEvent inputEvent)              | InputEvent からリクエストを文字列として取り出す                      |

getInputBody のクラス指定は、任意のJsonマッピングするクラスもしくは com.fasterxml.jackson.databind.JsonNode を指定します。

#### HttpResponse (io.github.tkote.fn.eventrouter.HttpResponse)

HTTP ステータス・コードをボディとセットにしてレスポンスする時に使用します。@FnHttpEvent メソッドの返り値にできます。

| メソッド                                                   | 説明                                                             |
|-----------------------------------------------------------|------------------------------------------------------------------|
| static HttpResponse jsonResponse(Object obj)              | 任意のオブジェクトから Json型のレスポンスを作成する, HTTP Status=200 |
| static HttpResponse jsonResponse(Object obj, int status)	 | 任意のオブジェクトから Json型のレスポンスを作成する                  |
| static HttpResponse textResponse(String str)	             | テキスト型のレスポンスを作成する, HTTP Status=200                   |
| static HttpResponse textResponse(String str, int status)  | テキスト型のレスポンスを作成する                                    |

### ロギング

Fn Event Router は java.util.logging を使用してログを出力します。
デフォルトの設定では、/logging.properteis リソースを読み込みます(パッケージのjarに含まれます)。
logging.properties は以下のような内容になっています。

```properties
handlers=java.util.logging.ConsoleHandler
.level=INFO

java.util.logging.ConsoleHandler.level=ALL
java.util.logging.ConsoleHandler.formatter=io.github.tkote.fn.eventrouter.logging.SimpleFormatter

java.util.logging.SimpleFormatter.format=%1$tY.%1$tm.%1$td %1$tH:%1$tM:%1$tS %4$s !host! %3$s: %5$s%6$s%n
```

io.github.tkote.fn.eventrouter.logging.SimpleFormatter というフォーマッタを使用していますが、これは java.util.logging.SimpleFormatter を使ってログをフォーマットした後さらに `!host!` をホスト名にリプレースします。

ログ設定の変更方法をいくつか提供しています。

1. 環境変数 もしくは Java システム・プロパティを使う方法

`logging:<key>=<value>` の書式で設定すると、既存の設定にこれらの設定がマージされます。同じ key があれば上書きします。

2. func.yaml もしくは OCI Functions の config で設定する方法

config 名 `LOGGING` で Java.util.logging の設定ファイルの記述内容をカンマ区切りで指定して下さい。

```yaml
config:
  LOGGING: io.github.tkote.fn.eventrouter.level=FINE, com.oracle.bmc.level=WARNING
```

この例では、Fn Event Router 関連のログ・レベルを FINE に、OCI SDK 関連のログ・レベルを WARNING に設定しています。

## その他

### Java Doc

[Java Doc はこちら](https://tkote.github.io/fn-event-router/apidocs/)

### 利用例

example ディレクトリにあります。



package org.apache.flink.agents.demo;

import org.apache.flink.agents.api.AgentsExecutionEnvironment;
import org.apache.flink.agents.api.resource.ResourceDescriptor;
import org.apache.flink.agents.api.resource.ResourceType;
import org.apache.flink.agents.integrations.chatmodel.ollama.OllamaChatModelConnectionDescriptor;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * A simple demo that runs the SimpleSentimentAgent on streaming text data.
 *
 * This demo can be run in two modes:
 * 1. File mode: reads from a text file (one text per line)
 * 2. Socket mode: reads from a socket (for interactive testing)
 */
public class SimpleSentimentDemo {

    public static void main(String[] args) throws Exception {
        // Parse arguments
        String mode = args.length > 0 ? args[0] : "file";
        String ollamaUrl = args.length > 1 ? args[1] : "http://localhost:11434";

        System.out.println("=== Simple Sentiment Analysis Demo ===");
        System.out.println("Mode: " + mode);
        System.out.println("Ollama URL: " + ollamaUrl);
        System.out.println();

        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Create Agents execution environment
        AgentsExecutionEnvironment agentsEnv = AgentsExecutionEnvironment.getExecutionEnvironment(env);

        // Configure Ollama chat model connection
        ResourceDescriptor ollamaDescriptor = ResourceDescriptor.Builder
                .newBuilder(OllamaChatModelConnectionDescriptor.class.getName())
                .addInitialArgument("baseUrl", ollamaUrl)
                .addInitialArgument("timeout", "120s")
                .build();

        agentsEnv.addResource("ollamaChatModelConnection", ResourceType.CHAT_MODEL_CONNECTION, ollamaDescriptor);

        // Create input data stream based on mode
        DataStream<String> textStream;

        if ("socket".equals(mode)) {
            String hostname = args.length > 2 ? args[2] : "localhost";
            int port = args.length > 3 ? Integer.parseInt(args[3]) : 9999;

            System.out.println("Reading from socket: " + hostname + ":" + port);
            System.out.println("Start a socket with: nc -lk " + port);
            System.out.println();

            textStream = env.socketTextStream(hostname, port);
        } else {
            // Default: read from file
            String filePath = args.length > 2 ? args[2] : "demo/src/main/resources/sample-texts.txt";

            System.out.println("Reading from file: " + filePath);
            System.out.println();

            FileSource<String> source = FileSource
                    .forRecordStreamFormat(new TextLineInputFormat(), new Path(filePath))
                    .build();

            textStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "file-source");
        }

        // Apply the sentiment agent to the stream
        DataStream<Object> resultStream = agentsEnv
                .fromDataStream(textStream)
                .apply(new SimpleSentimentAgent())
                .toDataStream();

        // Print results
        resultStream.print();

        // Execute the pipeline
        agentsEnv.execute("Simple Sentiment Analysis Demo");
    }
}

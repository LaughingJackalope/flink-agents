# Simple Sentiment Analysis Demo

A simple demonstration of Flink Agents that performs sentiment analysis on streaming text data using an LLM.

## Overview

This demo showcases:
- **SimpleSentimentAgent**: An agent that analyzes text sentiment using an LLM
- **Tool Calling**: The agent can use the `logEmotion` tool to record strong emotions
- **Stream Processing**: Processes text data in a streaming fashion using Apache Flink
- **Two Input Modes**: File-based and socket-based input

## Architecture

```
Input Text Stream → SimpleSentimentAgent → Sentiment Results
                           ↓
                    Ollama LLM (llama3.2)
                           ↓
                    Tool: logEmotion
```

## Prerequisites

1. **Java 11+** installed
2. **Maven** installed
3. **Ollama** running locally with `llama3.2` model

### Setting up Ollama

```bash
# Install Ollama (if not already installed)
# Visit: https://ollama.ai

# Pull the llama3.2 model
ollama pull llama3.2:latest

# Verify Ollama is running
curl http://localhost:11434/api/tags
```

## Building

From the project root directory:

```bash
# Build the entire project including the demo
mvn clean package -DskipTests

# Or build just the demo module
cd demo
mvn clean package
```

This creates a fat JAR at: `demo/target/flink-agents-demo-0.2-SNAPSHOT.jar`

## Running the Demo

### Mode 1: File Input (Default)

Process text from a file (one line = one text to analyze):

```bash
# From project root
java -jar demo/target/flink-agents-demo-0.2-SNAPSHOT.jar file http://localhost:11434 demo/src/main/resources/sample-texts.txt

# Or use default values
java -jar demo/target/flink-agents-demo-0.2-SNAPSHOT.jar
```

**Arguments:**
- Arg 1: `file` (mode)
- Arg 2: `http://localhost:11434` (Ollama URL, optional, default: localhost:11434)
- Arg 3: `demo/src/main/resources/sample-texts.txt` (input file path, optional)

### Mode 2: Socket Input (Interactive)

Process text from a socket for interactive testing:

```bash
# Terminal 1: Start the demo in socket mode
java -jar demo/target/flink-agents-demo-0.2-SNAPSHOT.jar socket http://localhost:11434 localhost 9999

# Terminal 2: Send text via netcat
nc -lk 9999
# Type messages and press Enter
I love this amazing product!
This is terrible and disappointing.
```

**Arguments:**
- Arg 1: `socket` (mode)
- Arg 2: `http://localhost:11434` (Ollama URL)
- Arg 3: `localhost` (socket hostname, optional, default: localhost)
- Arg 4: `9999` (socket port, optional, default: 9999)

## Expected Output

The demo prints sentiment analysis results in this format:

```
[SimpleSentimentAgent] Processing text: I absolutely love this product!
[SimpleSentimentAgent] LLM Response: SENTIMENT: POSITIVE
SCORE: 0.95
REASON: The text contains strong positive words like "absolutely love" and "exceeded all my expectations"
[EMOTION DETECTED] Type: JOY, Intensity: 9, Keywords: love, exceeded expectations
SentimentResult{sentiment='POSITIVE', score=0.95, reason='The text contains strong positive words...'}
```

## How It Works

### SimpleSentimentAgent

The agent follows this flow:

1. **Input Event Handler** (`processInput`)
   - Receives text from the stream
   - Creates a chat request with the text
   - Sends to the LLM

2. **LLM Processing**
   - The LLM analyzes sentiment based on the prompt
   - May call the `logEmotion` tool if strong emotions detected
   - Returns structured response

3. **Response Handler** (`processChatResponse`)
   - Parses LLM response
   - Extracts sentiment, score, and reason
   - Outputs `SentimentResult` object

### Key Components

- **@ChatModelSetup**: Configures the Ollama chat model
- **@Prompt**: Defines the system prompt for sentiment analysis
- **@Tool**: Defines the `logEmotion` tool for detecting strong emotions
- **@Action**: Event handlers for InputEvent and ChatResponseEvent

## Customizing the Demo

### Change the LLM Model

Edit `SimpleSentimentAgent.java`:

```java
@ChatModelSetup
public static ResourceDescriptor sentimentModel() {
    return ResourceDescriptor.Builder
            .newBuilder(OllamaChatModelSetup.class.getName())
            .addInitialArgument("connection", "ollamaChatModelConnection")
            .addInitialArgument("model", "qwen3:8b")  // Change model here
            .addInitialArgument("prompt", "sentimentPrompt")
            .build();
}
```

### Modify the Prompt

Edit the `SENTIMENT_PROMPT` constant in `SimpleSentimentAgent.java`.

### Add More Tools

Add new `@Tool` annotated methods to the agent:

```java
@Tool(description = "Save sentiment to database")
public static void saveSentiment(
        @ToolParam(name = "text") String text,
        @ToolParam(name = "sentiment") String sentiment) {
    // Implementation
}
```

### Use Your Own Data

Create a text file with one text per line:

```bash
echo "Your custom text here" > my-data.txt
java -jar demo/target/flink-agents-demo-0.2-SNAPSHOT.jar file http://localhost:11434 my-data.txt
```

## Deploying to Flink Cluster

To run on a real Flink cluster:

```bash
# Submit to Flink cluster
flink run -c org.apache.flink.agents.demo.SimpleSentimentDemo \
    demo/target/flink-agents-demo-0.2-SNAPSHOT.jar \
    file \
    http://ollama-server:11434 \
    /path/to/input.txt
```

Make sure:
- Ollama is accessible from the Flink cluster nodes
- Input file path is accessible from the cluster

## Troubleshooting

### "Connection refused" to Ollama

- Ensure Ollama is running: `ollama serve`
- Check the URL: `curl http://localhost:11434/api/tags`
- Update the Ollama URL argument if running remotely

### "Model not found"

```bash
# Pull the model
ollama pull llama3.2:latest
```

### "No output" or "Empty results"

- Check that the input file exists and has content
- Verify Ollama is responding: `ollama run llama3.2 "test"`
- Check for errors in the console output

## Next Steps

- Modify the agent to use different LLM providers
- Add more sophisticated tools (database, API calls)
- Chain multiple agents together
- Process data from Kafka or other sources
- Add windowing and aggregation logic

## License

Apache License 2.0

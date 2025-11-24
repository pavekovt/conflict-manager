# AI Provider Setup Guide

This guide explains how to configure and use the AI-powered relationship advice system.

## Overview

The system supports two AI providers:
- **MockAIProvider**: Simple mock implementation for development/testing (default)
- **ClaudeAIProvider**: Production-ready implementation using Anthropic's Claude API

## Configuration

### Option 1: Environment Variables (Recommended for Production)

Set these environment variables:

```bash
export AI_PROVIDER=claude
export CLAUDE_API_KEY=your-api-key-here
export AI_MODEL=claude-3-5-sonnet-20241022  # Optional, this is the default
```

### Option 2: Configuration File

Edit `backend/src/main/resources/application.yaml`:

```yaml
ai:
  provider: claude
  apiKey: your-api-key-here
  model: claude-3-5-sonnet-20241022
```

### Option 3: .env File

Copy `.env.example` to `.env` and set your values:

```bash
cp backend/.env.example backend/.env
# Edit .env and set CLAUDE_API_KEY
```

## Getting Claude API Key

1. Visit [Anthropic Console](https://console.anthropic.com/)
2. Sign up or log in
3. Navigate to API Keys section
4. Create a new API key
5. Copy the key and set it in your configuration

## AI Provider Features

### ClaudeAIProvider Capabilities

The Claude AI provider provides:

1. **Context-Aware Conflict Resolution**
   - Analyzes both partners' resolutions
   - Uses relationship history for pattern detection
   - Generates neutral summaries ("We decided that...")
   - Identifies recurring themes
   - Provides actionable relationship advice

2. **Retrospective Discussion Points**
   - Groups notes by theme
   - Suggests discussion approaches
   - Prioritizes important topics

3. **Partnership Context Management**
   - Automatically compacts relationship history
   - Tracks patterns and communication styles
   - Maintains concise summaries for fast AI processing

### Example AI Response Structure

```json
{
  "summary": "We decided that both partners will set clearer expectations about response times for messages. We agreed that urgent matters will be marked as such, and non-urgent messages don't require immediate responses.",

  "patterns": "Based on your relationship history, this is your third discussion about communication expectations. Both of you value responsiveness but have different definitions of 'urgent'. This pattern suggests you may benefit from a more structured communication protocol.",

  "advice": "1. Create a shared document defining what constitutes 'urgent' vs. 'non-urgent'\n2. Set specific time windows when you both commit to being responsive (e.g., 6-9pm)\n3. Schedule a follow-up check-in in one week to assess if the new system is working",

  "recurring_issues": ["Communication expectations", "Response time concerns"],

  "theme_tags": ["communication", "expectations", "boundaries"]
}
```

## Testing the Integration

### 1. Start with Mock Provider

First, test the system with the mock provider (default):

```bash
cd backend
./gradlew run
```

The mock provider will respond to API calls without making external requests.

### 2. Switch to Claude Provider

Set your API key and change the provider:

```bash
export AI_PROVIDER=claude
export CLAUDE_API_KEY=your-key-here
cd backend
./gradlew run
```

### 3. Test Conflict Resolution Flow

```bash
# Register two users and create partnership (see Postman collection)
# Create a conflict
# Both users submit resolutions
# AI summary will be generated using Claude

# Check the response - it should include:
# - Enhanced summary
# - Pattern analysis
# - Actionable advice
# - Theme tags
```

## Cost Considerations

Claude API pricing (as of 2024):
- **Input**: ~$3 per million tokens
- **Output**: ~$15 per million tokens

Typical usage per conflict:
- Input: ~500-1000 tokens (resolution texts + context)
- Output: ~300-500 tokens (summary + advice)
- **Cost per conflict**: ~$0.01-0.02

For a couple resolving 10 conflicts per month: ~$0.10-0.20/month

## Monitoring and Debugging

### Enable Detailed Logging

The ClaudeAIProvider logs all API calls. Check logs for:

```
INFO  ClaudeAIProvider - Calling Claude API for conflict summarization
ERROR ClaudeAIProvider - Claude API error: 401 - Invalid API key
```

### Common Issues

**Issue**: "Claude API key not configured"
- **Solution**: Set `CLAUDE_API_KEY` environment variable or `ai.apiKey` in config

**Issue**: "401 Unauthorized"
- **Solution**: Verify your API key is correct and active

**Issue**: "429 Too Many Requests"
- **Solution**: You've exceeded rate limits. Wait or upgrade your plan.

**Issue**: "AI service temporarily unavailable"
- **Solution**: Check your internet connection and Anthropic API status

## Fallback Strategy

If Claude API is unavailable, the system will throw an error. Consider:

1. Implementing retry logic
2. Falling back to MockAIProvider automatically
3. Queuing conflicts for later AI processing

## Security Best Practices

1. **Never commit API keys** to version control
2. **Use environment variables** in production
3. **Rotate API keys** periodically
4. **Monitor API usage** to detect anomalies
5. **Restrict API key permissions** to minimum required

## Advanced Configuration

### Custom Prompts

To customize AI behavior, edit the prompts in `ClaudeAIProvider.kt`:

- `CONFLICT_SYSTEM_PROMPT`: Controls conflict resolution advice tone
- `RETRO_SYSTEM_PROMPT`: Controls retrospective discussion generation
- `CONTEXT_UPDATE_SYSTEM_PROMPT`: Controls history compression

### Using Different Models

Claude offers multiple models:

- `claude-3-5-sonnet-20241022` (Recommended): Best balance of speed/quality
- `claude-3-opus-20240229`: Highest quality, slower, more expensive
- `claude-3-haiku-20240307`: Fastest, cheapest, good for simple tasks

Set via `AI_MODEL` environment variable or `ai.model` config.

## Performance Optimization

### Context Size Management

The system automatically limits context to 2000 characters. For very active partnerships:

1. Older conflicts are summarized into patterns
2. Context focuses on recent themes
3. Full history is still stored in database

### Batch Processing

For high-volume scenarios, consider:

1. Processing conflicts asynchronously
2. Caching AI responses for similar resolutions
3. Rate limiting conflict creation

## Support

For issues with:
- **AI Integration**: Check this guide and application logs
- **Claude API**: Visit [Anthropic Documentation](https://docs.anthropic.com/)
- **Application Bugs**: Check project issue tracker

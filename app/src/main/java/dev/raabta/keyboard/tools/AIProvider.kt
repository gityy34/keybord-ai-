package dev.raabta.keyboard.tools

/** Provider-neutral operations. The UI never constructs an HTTP request. */
interface AIProvider {
    fun generateText(instruction:String,text:String,variants:Boolean=false):String
    fun fixGrammar(text:String,language:String)=generateText(AiTools.instruction("correct",language),text)
    fun rewrite(text:String,style:String,language:String)=generateText(AiTools.instruction(style,language),text)
    fun changeTone(text:String,tone:String,language:String)=rewrite(text,tone,language)
    fun summarize(text:String,language:String)=rewrite(text,"summary",language)
    fun translate(text:String,source:String,target:String)=generateText("Translate from $source to $target; preserve meaning and formatting.",text)
    fun testConnection():String=generateText("Reply with the single word OK.","Connection test")
    fun cancel()
}

enum class ProviderKind(val label:String,val endpoint:String,val defaultModel:String) {
    GROQ("Groq","https://api.groq.com/openai/v1/chat/completions","openai/gpt-oss-20b"),
    CUSTOM("Custom HTTPS / OpenAI-compatible","","")
}

enum class AiProblem(val userMessage:String) {
    AUTH("Authentication failed. Check your API key."), MODEL("Model unavailable. Choose a supported model."),
    RATE("Rate limit reached. Wait and retry."), TIMEOUT("Request timed out. Retry when your connection improves."),
    NETWORK("Network unavailable. Check your Internet connection."), UNAVAILABLE("AI provider unavailable. Retry later."),
    RESPONSE("The provider returned an unusable response."), REQUEST("The provider could not process this request."),
    DISABLED("No active connection. Open Connections to activate a provider."), CANCELLED("Request cancelled.")
}
class AiFailure(val problem:AiProblem):Exception(problem.userMessage)

class OpenAiCompatibleProvider(private val endpoint:String,private val model:String,private val token:String,
    private val request:AiRequest=AiRequest()):AIProvider {
    override fun generateText(instruction:String,text:String,variants:Boolean)=request.generate(endpoint,model,token,instruction,text,variants)
    override fun cancel()=request.cancel()
}

/** Resolves a snapshot once per operation; changes cannot silently redirect an in-flight request. */
class AIService(private val connection:AiConnection,private val requestFactory:()->AiRequest = { AiRequest() }) {
    fun provider(forTest:Boolean=false):AIProvider {
        if(!connection.configured() || (!forTest && !connection.active())) throw AiFailure(AiProblem.DISABLED)
        val token=try { connection.token() } catch (_:Exception) { throw AiFailure(AiProblem.AUTH) }
        if(connection.provider()==ProviderKind.GROQ && token.isEmpty()) throw AiFailure(AiProblem.AUTH)
        return OpenAiCompatibleProvider(connection.endpoint(),connection.model(),token,requestFactory())
    }
}

package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val BASE_URL = "http://192.168.0.102:9999"
private val gson = Gson()
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client).map { post ->
                    async {
                        PostWithAuthorAndComments(
                            post,
                            getAuthors(client, post.authorId),
                            getCommentsWithAuthor(client, post.id)
                        )
                    }
                }.awaitAll()
                println(posts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })

    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }


suspend fun getPosts(client: OkHttpClient ): List<Post> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getCommentsWithAuthor(client: OkHttpClient, postId: Long): List<CommentWithAuthor> {
    val comments = getComments(client, postId)
    return comments.map { comment ->
        CommentWithAuthor(comment, getAuthors(client, comment.authorId))
    }
}

suspend fun getComments(client: OkHttpClient, postId: Long): List<Comment> =
    makeRequest("$BASE_URL/api/slow/posts/$postId/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthors(client: OkHttpClient, authorId: Long): Author =
    makeRequest("$BASE_URL/api/authors/$authorId", client, object : TypeToken<Author>() {})

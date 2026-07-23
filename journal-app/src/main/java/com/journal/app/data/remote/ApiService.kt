package com.journal.app.data.remote

import com.journal.app.data.remote.dto.AuthRequest
import com.journal.app.data.remote.dto.AuthResponse
import com.journal.app.data.remote.dto.IceBreakRequest
import com.journal.app.data.remote.dto.JournalResponse
import com.journal.app.data.remote.dto.MatchResponse
import com.journal.app.data.remote.dto.SyncPayload
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ── Auth ──
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    // ── Journals ──
    @GET("api/v1/journals")
    suspend fun getJournals(
        @Query("from") from: String,
        @Query("to") to: String,
    ): Response<List<JournalResponse>>

    @GET("api/v1/journals/{date}")
    suspend fun getJournal(@Path("date") date: String): Response<JournalResponse>

    @PUT("api/v1/journals/{date}/summary")
    suspend fun updateSummary(
        @Path("date") date: String,
        @Body body: Map<String, String>,
    ): Response<Unit>

    @PUT("api/v1/journals/{date}/mood")
    suspend fun updateMood(
        @Path("date") date: String,
        @Body body: Map<String, String>,
    ): Response<Unit>

    // ── Entries ──
    @Multipart
    @POST("api/v1/entries")
    suspend fun uploadEntry(
        @Part("entry") entry: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<Unit>

    @PUT("api/v1/entries/{id}/tags")
    suspend fun updateTags(
        @Path("id") entryId: String,
        @Body tags: Map<String, List<Map<String, String>>>,
    ): Response<Unit>

    @PUT("api/v1/entries/{id}/star")
    suspend fun toggleStar(@Path("id") entryId: String): Response<Unit>

    @DELETE("api/v1/entries/{id}")
    suspend fun deleteEntry(@Path("id") entryId: String): Response<Unit>

    // ── Matches ──
    @GET("api/v1/matches/daily")
    suspend fun getDailyMatches(): Response<List<MatchResponse>>

    @POST("api/v1/matches/{id}/accept")
    suspend fun acceptMatch(@Path("id") matchId: String): Response<Unit>

    @POST("api/v1/matches/{id}/skip")
    suspend fun skipMatch(@Path("id") matchId: String): Response<Unit>

    @POST("api/v1/matches/{id}/message")
    suspend fun sendIceBreakMessage(
        @Path("id") matchId: String,
        @Body request: IceBreakRequest,
    ): Response<Unit>

    // ── Sync ──
    @POST("api/v1/sync/push")
    suspend fun pushEntries(@Body payload: SyncPayload): Response<Unit>

    @GET("api/v1/sync/pull")
    suspend fun pullUpdates(@Query("since") since: Long): Response<SyncPayload>
}

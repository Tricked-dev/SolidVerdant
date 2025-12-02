package dev.tricked.solidverdant.data.remote

import dev.tricked.solidverdant.data.model.MembershipsResponse
import dev.tricked.solidverdant.data.model.ProjectsResponse
import dev.tricked.solidverdant.data.model.StartTimeEntryRequest
import dev.tricked.solidverdant.data.model.StopTimeEntryRequest
import dev.tricked.solidverdant.data.model.TasksResponse
import dev.tricked.solidverdant.data.model.TimeEntryResponse
import dev.tricked.solidverdant.data.model.TokenResponse
import dev.tricked.solidverdant.data.model.UserResponse
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Retrofit API interface for Solidtime API
 */
interface SolidtimeApi {

    /**
     * Exchange authorization code for access and refresh tokens
     */
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeCodeForToken(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("client_id") clientId: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("code") code: String
    ): TokenResponse

    /**
     * Refresh the access token using the refresh token
     */
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String,
        @Field("refresh_token") refreshToken: String
    ): TokenResponse

    /**
     * Get the current authenticated user
     */
    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): UserResponse

    /**
     * Get all memberships (organizations) for the current user
     */
    @GET("api/v1/users/me/memberships")
    suspend fun getMyMemberships(): MembershipsResponse

    /**
     * Get the active time entry for the current user
     */
    @GET("api/v1/users/me/time-entries/active")
    suspend fun getActiveTimeEntry(): TimeEntryResponse

    /**
     * Start a new time entry
     */
    @POST("api/v1/organizations/{organization}/time-entries")
    suspend fun startTimeEntry(
        @Path("organization") organizationId: String,
        @Body request: StartTimeEntryRequest
    ): TimeEntryResponse

    /**
     * Stop an active time entry
     */
    @PUT("api/v1/organizations/{organization}/time-entries/{id}")
    suspend fun stopTimeEntry(
        @Path("organization") organizationId: String,
        @Path("id") timeEntryId: String,
        @Body request: StopTimeEntryRequest
    ): TimeEntryResponse

    /**
     * Get all projects for an organization
     */
    @GET("api/v1/organizations/{organization}/projects?archived=all")
    suspend fun getProjects(
        @Path("organization") organizationId: String
    ): ProjectsResponse

    /**
     * Get all tasks for an organization
     */
    @GET("api/v1/organizations/{organization}/tasks?done=all")
    suspend fun getTasks(
        @Path("organization") organizationId: String
    ): TasksResponse
}

package site.iplease.accountserver.domain.profile.endpoint.controller

import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import site.iplease.accountserver.domain.profile.dto.ProfileDto
import site.iplease.accountserver.domain.profile.response.CommonProfileResponse
import site.iplease.accountserver.domain.profile.response.ProfileResponse
import site.iplease.accountserver.domain.profile.response.StudentProfileResponse
import site.iplease.accountserver.domain.profile.response.TeacherProfileResponse
import site.iplease.accountserver.domain.profile.service.ProfileService
import site.iplease.accountserver.global.common.type.AccountType

@Validated
@RestController
@RequestMapping("/api/v1/account/profile")
class ProfileController(
    private val profileService: ProfileService
) {
    @GetMapping("/access-token/{token}")
    fun getMyProfile(@PathVariable token: String): Mono<ResponseEntity<ProfileResponse>> =
        profileService.getProfileByAccessToken(token)
            .map { profile -> profile.toResponse() }
            .map { response -> ResponseEntity.ok(response) }

    @GetMapping("/id/{id}")
    fun getProfileByAccountId(@PathVariable id: Long): Mono<ResponseEntity<ProfileResponse>> =
        profileService.getProfileByAccountId(id)
            .map { profile -> profile.toResponse() }
            .map { response -> ResponseEntity.ok(response) }

    private fun ProfileDto.toResponse() =
        ProfileResponse(
            type = type,
            common = CommonProfileResponse(
                accountId = accountId,
                permission = permission,
                name = name,
                email = email,
                profileImage = profileImage.toString()
            ),
            teacher = if(type == AccountType.TEACHER) TeacherProfileResponse() else null,
            student = if(type == AccountType.STUDENT) StudentProfileResponse(
                studentNumber = studentNumber,
                department = department
            ) else null
        )
}
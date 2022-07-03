package site.iplease.accountserver.domain.profile.util

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import site.iplease.accountserver.domain.auth.data.dto.AuthDto
import site.iplease.accountserver.domain.profile.data.dto.ProfileDto
import site.iplease.accountserver.domain.profile.data.request.UpdateProfileRequest
import site.iplease.accountserver.domain.profile.data.response.ChangePasswordRequest
import site.iplease.accountserver.global.auth.data.dto.AuthTokenDto
import site.iplease.accountserver.global.auth.data.type.AuthType
import site.iplease.accountserver.global.auth.exception.WrongEmailTokenException
import site.iplease.accountserver.global.auth.util.atomic.AuthTokenDecoder
import site.iplease.accountserver.global.common.entity.Account
import site.iplease.accountserver.global.common.exception.PolicyViolationException
import site.iplease.accountserver.global.common.repository.AccountRepository
import site.iplease.accountserver.global.common.type.DepartmentType
import site.iplease.accountserver.global.common.type.PolicyType
import java.net.URI
import javax.imageio.ImageIO

@Component
class ProfileCommandPreprocessorImpl(
    private val authTokenDecoder: AuthTokenDecoder,
    private val accountRepository: AccountRepository,
    private val profileConverter: ProfileConverter
): ProfileCommandPreprocessor {
    //비밀번호 정책을 검사사한다.
    override fun validate(accountId: Long, request: ChangePasswordRequest): Mono<Account> =
        //이메일 토큰을 decode한다.
        authTokenDecoder.decode(AuthTokenDto(token = request.emailToken))
            //decode해서 얻은 인증토큰을 validate한다. (인증토큰이 유효한 이메일 토큰인가)
            .flatMap { dto -> validateAuthToken(dto).map { dto.data } }//TODO emailTokenValidator.validate(authDto)
            //decode해서 얻은 email을 validate한다. (요청자가 해당email의 소유자인가)
            .flatMap { email -> validateAccountOwner(accountId, email) }//TODO accountOwnerValidator.validate(accountId, email);

    //프로필 업데이트 정책을 검사한다.
    override fun validate(accountId: Long, request: UpdateProfileRequest): Mono<ProfileDto> =
        //이메일 토큰을 decode한다.
        authTokenDecoder.decode(AuthTokenDto(token = request.emailToken))
            //decode해서 얻은 인증토큰을 validate한다. (인증토큰이 유효한 이메일 토큰인가)
            .flatMap { dto -> validateAuthToken(dto).map { dto.data } }//TODO emailTokenValidator.validate(authDto)
            //decode해서 얻은 email을 validate한다. (요청자가 해당email의 소유자인가)
            .flatMap { email -> validateAccountOwner(accountId, email) }//TODO accountOwnerValidator.validate(accountId, email);
            //요청정보와 요청자 id를 프로필Dto로 convert한다.
            .flatMap { convertChangedProfile(accountId, request) }
            //convert해서 얻은 프로필Dto를 validate한다. (프로필 정보가 유효한가)
            .flatMap { profileDto -> validateProfilePolicy(profileDto) } //TODO profilePolicyValidator.validate(profileDto)

    private fun convertChangedProfile(accountId: Long, request: UpdateProfileRequest) =/////
        accountRepository.findById(accountId) //인자로 받은 accountId를 통해 프로필을 변경할 계정을 찾는다.
            .flatMap { account -> makeProfileDto(request, account) } //계정과 인자로 받은 프로필 변경 요청 정보를 통해 변경될 프로필을 생성한다.

    fun validateAuthToken(dto: AuthDto) =/////
        if (dto.type == AuthType.EMAIL) Unit.toMono()
        else Mono.error(WrongEmailTokenException("해당 토큰은 ${dto.type}토큰입니다!"))

    fun validateAccountOwner(accountId: Long, email: String) =/////
        accountRepository.findByEmail(email)
            .flatMap { account ->
                if (account.id == accountId) account.toMono()
                else Mono.error(WrongEmailTokenException("해당 토큰은 다른 사용자의 인증토큰입니다! - ${account.id}"))
            }

    private fun validateProfilePolicy(dto: ProfileDto): Mono<ProfileDto> =/////
        dto.toMono()
            .flatMap { validateNamePolicy(dto.name) }
            .flatMap { validateProfileImagePolicy(dto.profileImage) }
            .flatMap { validateStudentNumber(dto.studentNumber) }
            .flatMap { validateDepartment(dto.department, dto.studentNumber) }
            .map { dto }

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    private fun validateDepartment(department: DepartmentType, studentNumber: Int): Mono<Unit> =
        getClassByStudentNumber(studentNumber).toMono()
            .map { clazz -> department.classes.contains(clazz) }
            .flatMap { isMatch -> //학과와 반이 일치하는가
                if (isMatch) Unit.toMono()
                else Mono.error(PolicyViolationException(PolicyType.DEPARTMENT, "학과와 반이 일치하지 않습니다!"))
            }

    private fun getClassByStudentNumber(studentNumber: Int) = studentNumber%1000/100
    private fun validateStudentNumber(studentNumber: Int): Mono<Unit> =
        Regex("[0-3][1-4][0-2]\\d").toMono()
            .map { regex -> studentNumber.toString().matches(regex) }
            .flatMap { isMatch ->
                if (isMatch) Unit.toMono()
                else Mono.error(PolicyViolationException(PolicyType.STUDENT_NUMBER, "학번이 올바르지 않습니다!"))
            }

    private fun validateProfileImagePolicy(profileImage: URI): Mono<Unit> =
        Unit.toMono()
            .map { ImageIO.read(profileImage.toURL()) != null }
            .onErrorReturn(false)
            .flatMap { isFormed ->
                if (isFormed) Unit.toMono()
                else Mono.error(PolicyViolationException(PolicyType.PROFILE_IMAGE, "프로필 이미지가 올바르지 않습니다!"))
            }

    private fun validateNamePolicy(name: String): Mono<Unit> =
        name.toMono()
            .map { it.isNotEmpty() && it.length <= 5 && it.length >= 2 }
            .flatMap { isMatch ->
                if (isMatch) Unit.toMono()
                else Mono.error(PolicyViolationException(PolicyType.NAME, "이름이 올바르지 않습니다!"))
            }

    //이메일 토큰을 해석한다.
    private fun decodeEmailToken(emailToken: String) = //TODO auth 도메인에 emailTokenDecoder 만들어서 해당 유틸로 메서드 이전하기
        authTokenDecoder.decode(AuthTokenDto(token = emailToken))
            .flatMap { dto ->
                if (dto.type == AuthType.EMAIL) dto.data.toMono()
                else Mono.error(WrongEmailTokenException("해당 토큰은 ${dto.type}토큰입니다!"))
            }

    //프로필 요청과 계정엔티티로 변경될 프로필 Dto를 작성한다.
    private fun makeProfileDto(request: UpdateProfileRequest, account: Account) =
        account.toMono()
            .flatMap { //신규 이메일로 변경될 경우, 이메일토큰을 검증하여 새 이메일을 확인한다.
                if(request.newEmailToken != null && request.newEmailToken.isNotEmpty()) decodeEmailToken(request.newEmailToken)
                else account.email.toMono()
            }.flatMap { email -> profileConverter.toDto(request, account, email) }//이메일, 기존 계정 정보, 프로필 변경 요청정보를 통해 변경될 프로필을 생성한다.
}
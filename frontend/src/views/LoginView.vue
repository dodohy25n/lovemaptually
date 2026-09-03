<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import raccoon from '../../frontend-assets/mascots/rubia_raccoon_waving.png'
import heartBurst from '../../frontend-assets/decorations/crayon_heart_burst.png'
import pinkTape from '../../frontend-assets/decorations/love_maptually_pink_tape.png'
import heartFlourish from '../../frontend-assets/decorations/love_maptually_heart_flourish.png'
import loveStamp from '../../frontend-assets/decorations/love_stamp_red.png'

const router = useRouter()
const form = reactive({ email: '', password: '', remember: false })
const showPassword = ref(false)
const notice = ref('')
const canSubmit = computed(() => /.+@.+\..+/.test(form.email) && form.password.length >= 8)

function submit() {
  if (!canSubmit.value) return
  notice.value = '로그인 정보를 확인했어요. 홈으로 이동합니다. ♡'
  window.setTimeout(() => router.push('/map'), 350)
}

</script>

<template>
  <main class="login-page">
    <img class="login-page__burst burst--top" :src="heartBurst" alt="" aria-hidden="true" />
    <img class="login-page__burst burst--bottom" :src="heartBurst" alt="" aria-hidden="true" />

    <header class="login-head">
      <img :src="heartFlourish" alt="" aria-hidden="true" />
      <h1>Love Maptually</h1>
      <p>우리만의 특별한 데이트 이야기를<br />시작해보세요! 💕</p>
    </header>

    <div class="login-layout">
      <aside class="login-guide">
        <div class="speech"><strong>안녕! 나는 러비야!</strong><br />궁금한 게 있으면<br />무엇이든 물어봐줘! ♡</div>
        <img :src="raccoon" alt="손을 흔드는 러비" />
        <img class="postmark" :src="loveStamp" alt="" aria-hidden="true" />
      </aside>

      <form class="login-card" data-testid="login-form" @submit.prevent="submit">
        <img class="login-card__tape" :src="pinkTape" alt="" aria-hidden="true" />
        <label class="login-field">
          <span class="sr">이메일</span>
          <input v-model.trim="form.email" type="email" autocomplete="email" placeholder="이메일을 입력해주세요" required />
        </label>
        <label class="login-field">
          <span class="sr">비밀번호</span>
          <input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" minlength="8" placeholder="비밀번호를 입력해주세요" required />
          <button type="button" class="password-toggle" :aria-label="showPassword ? '비밀번호 숨기기' : '비밀번호 보기'" @click="showPassword = !showPassword">{{ showPassword ? '숨김' : '보기' }}</button>
        </label>

        <div class="login-options">
          <label><input v-model="form.remember" type="checkbox" /> 로그인 상태 유지</label>
          <button type="button">비밀번호 찾기</button>
        </div>

        <button class="login-submit" type="submit" :disabled="!canSubmit" data-testid="login-submit">로그인</button>

        <p class="join">아직 러비가 아니신가요? <RouterLink to="/signup">회원가입하기 ›</RouterLink></p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      </form>
    </div>
  </main>
</template>

<style scoped>
.login-page{position:relative;width:min(1040px,calc(100vw - 32px));min-height:740px;margin:18px auto;padding:66px 54px 38px;overflow:hidden;border:2px solid #f1c6bf;border-radius:30px;background:linear-gradient(rgba(255,250,245,.86),rgba(255,250,245,.86)),url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center/560px;box-shadow:0 22px 60px rgba(103,66,55,.18);color:#5e4740}.login-page:before{content:'';position:absolute;left:32px;right:32px;top:0;height:35px;background:radial-gradient(circle at 14px 12px,#ab7067 0 6px,transparent 6.5px);background-size:52px 32px}.login-page:after{content:'';position:absolute;right:-42px;bottom:-44px;width:255px;height:275px;opacity:.36;background:linear-gradient(90deg,transparent 48%,#e7d9d2 49% 51%,transparent 52%),linear-gradient(transparent 48%,#e7d9d2 49% 51%,transparent 52%);background-size:58px 58px;transform:rotate(-8deg)}.login-page__burst{position:absolute;width:120px;opacity:.8}.burst--top{left:42px;top:58px}.burst--bottom{right:30px;bottom:15px;transform:rotate(15deg)}.login-head{position:relative;z-index:1;text-align:center}.login-head>img{width:60px;height:48px;object-fit:contain}.login-head h1{margin-top:3px;font-family:Georgia,serif;font-size:39px;color:#ed6680}.login-head strong{display:block;margin-top:2px;color:#f06b84;font-size:14px}.login-head p{margin-top:18px;font-size:15px;line-height:1.55}.login-layout{position:relative;z-index:2;display:grid;grid-template-columns:250px minmax(0,470px);justify-content:center;gap:35px;align-items:center;margin-top:25px}.login-guide{text-align:center}.speech{position:relative;width:192px;margin:0 auto -5px;padding:18px 20px;border:2px dashed #efb9b5;border-radius:15px;background:rgba(255,249,245,.9);font-size:13px;line-height:1.7;text-align:left;box-shadow:0 6px 14px rgba(128,97,89,.1)}.speech strong{color:#ed667d}.speech:after{content:'';position:absolute;left:80px;bottom:-10px;width:17px;height:17px;background:#fff9f5;border-right:2px dashed #efb9b5;border-bottom:2px dashed #efb9b5;transform:rotate(45deg)}.login-guide>img{display:block;width:205px;height:205px;margin:0 auto -16px;object-fit:contain}.postmark{width:88px;height:60px;margin:0 auto;border:2px dashed #efb9b5;border-radius:50%;color:#ef9ba7;font:italic 22px Georgia,serif;line-height:56px;transform:rotate(-8deg);opacity:.8}.login-card{position:relative;display:flex;flex-direction:column;gap:12px;min-height:396px;padding:34px 32px 22px;border:1px solid #eed7d0;border-radius:18px;background:rgba(255,253,250,.89);box-shadow:0 8px 24px rgba(128,97,89,.14)}.login-card__tape{position:absolute;right:-58px;top:-57px;width:150px;transform:rotate(18deg);pointer-events:none}.login-field{display:flex;align-items:center;gap:12px;height:52px;padding:0 15px;border:1px solid #e8d7d1;border-radius:12px;background:#fff;color:#9a7b72}.login-field input{flex:1;min-width:0;border:0;outline:0;background:transparent;font-size:13px;color:#56413b}.password-toggle{padding:6px;color:#9a7b72;font-size:17px}.login-options{display:flex;align-items:center;justify-content:space-between;font-size:12px}.login-options label{display:flex;align-items:center;gap:8px}.login-options input{width:18px;height:18px;accent-color:#ef6680}.login-options button{color:#67504a}.login-submit{height:50px;border-radius:13px;background:linear-gradient(90deg,#ff8ca7,#f05b79);color:#fff;font-size:17px;font-weight:800;box-shadow:0 7px 16px rgba(240,91,121,.2)}.login-submit:disabled{cursor:not-allowed;opacity:.45}.divider{display:flex;align-items:center;gap:18px;color:#8e756e;font-size:12px}.divider:before,.divider:after{content:'';flex:1;border-top:1px solid #e6d6d0}.socials{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.socials button{display:flex;height:58px;flex-direction:column;align-items:center;justify-content:center;gap:3px;border:1px solid #e8d7d1;border-radius:11px;background:#fff;font-size:10px;color:#64504a}.socials b{display:grid;place-items:center;height:22px;font-size:20px}.socials .kakao{width:22px;height:22px;border-radius:50%;background:#f9df00;color:#251f13;font-size:6px}.socials .naver{color:#20bd55}.socials .google{color:#4285f4}.socials .apple{color:#242424}.join{text-align:center;color:#7e665f;font-size:11.5px}.join a{font-weight:800;color:#ee6680}.notice{margin-top:-3px;text-align:center;color:#d6536d;font-size:11px}.sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}
.login-head h1{font-family:var(--lm-font-logo);font-size:48px;font-weight:400}.login-head p{margin-top:12px}.password-toggle{font-size:11px}.socials button{height:54px;flex-direction:row;gap:6px;font-size:11px}.socials button>img{width:23px;height:23px;object-fit:contain}.socials button>img.apple-logo{width:21px;height:21px}.socials b{display:none}.login-guide{position:relative}.login-guide>img.postmark{position:absolute;left:-86px;bottom:-48px;display:block;width:80px;height:80px;margin:0;border:0;border-radius:0;object-fit:contain;line-height:normal;transform:rotate(-10deg);opacity:.72}
@media(max-width:760px){.login-page{min-height:0;padding:70px 20px 30px}.login-layout{grid-template-columns:1fr;gap:18px}.login-guide{order:2}.login-guide>img{width:145px;height:145px}.postmark{display:none}.login-card{padding:28px 18px}.socials{gap:6px}.login-page__burst{width:80px}.burst--top{left:-12px}.login-head h1{font-size:38px}}
</style>

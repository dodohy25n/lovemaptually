<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { signup } from '@/services/authApi.js'
import raccoon from '../../frontend-assets/mascots/rubia_raccoon_waving.png'
import heartBurst from '../../frontend-assets/decorations/crayon_heart_burst.png'
import pinkTape from '../../frontend-assets/decorations/love_maptually_pink_tape.png'
import heartFlourish from '../../frontend-assets/decorations/love_maptually_heart_flourish.png'

const router = useRouter()
const form = reactive({ email: '', password: '', passwordConfirm: '', nickname: '', gender: '여성', birthDate: '', styles: [], agreed: false })
const dateStyles = ['맛집 탐방', '카페 데이트', '감성 여행', '액티비티', '전시·공연']
const passwordMismatch = computed(() => form.passwordConfirm && form.password !== form.passwordConfirm)
const canSubmit = computed(() => form.email && form.password.length >= 8 && !passwordMismatch.value && form.nickname && form.birthDate && form.styles.length && form.agreed)
const submitting = ref(false)
const error = ref('')

function toggleStyle(style) {
  const index = form.styles.indexOf(style)
  if (index >= 0) form.styles.splice(index, 1)
  else form.styles.push(style)
}
async function submit() {
  if (!canSubmit.value || submitting.value) return
  error.value = ''
  submitting.value = true
  try {
    await signup({ email: form.email, password: form.password, nickname: form.nickname })
    await router.push('/login')
  } catch (err) {
    error.value = err?.message || '회원가입하지 못했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="signup-page">
    <img class="signup-page__burst" :src="heartBurst" alt="" aria-hidden="true" />
    <button type="button" class="back" aria-label="이전 화면으로" @click="router.back()">‹</button>

    <header class="signup-head">
      <img :src="heartFlourish" alt="" aria-hidden="true" />
      <h1>회원가입</h1>
      <strong>Love Maptually</strong>
      <p>우리의 이야기를 함께 만들어봐요! ♡</p>
    </header>

    <div class="signup-layout">
      <form class="signup-form" data-testid="signup-form" @submit.prevent="submit">
        <img class="signup-form__tape" :src="pinkTape" alt="" aria-hidden="true" />
        <label><span class="sr">이메일</span><input v-model.trim="form.email" type="email" autocomplete="email" placeholder="이메일을 입력해주세요" required /></label>
        <label><span class="sr">비밀번호</span><input v-model="form.password" type="password" autocomplete="new-password" minlength="8" placeholder="비밀번호를 입력해주세요 (8자 이상)" required /></label>
        <label><span class="sr">비밀번호 확인</span><input v-model="form.passwordConfirm" type="password" autocomplete="new-password" placeholder="비밀번호 확인" required /></label>
        <p v-if="passwordMismatch" class="field-error" role="alert">비밀번호가 일치하지 않아요.</p>
        <label><span class="sr">닉네임</span><input v-model.trim="form.nickname" maxlength="12" placeholder="닉네임을 입력해주세요 (2~12자)" required /></label>

        <fieldset><legend>성별</legend><div class="gender"><label v-for="gender in ['여성','남성']" :key="gender" :class="{selected:form.gender===gender}"><input v-model="form.gender" type="radio" :value="gender" />{{ gender }}</label></div></fieldset>
        <label class="field"><span>생년월일</span><input v-model="form.birthDate" type="date" required /></label>

        <fieldset><legend>데이트 스타일 <small>(복수 선택 가능)</small></legend><div class="style-chips"><button v-for="style in dateStyles" :key="style" type="button" :class="{selected:form.styles.includes(style)}" @click="toggleStyle(style)">{{ style }}</button></div></fieldset>
        <label class="agree"><input v-model="form.agreed" type="checkbox" /> 이용약관 및 개인정보 수집·이용에 동의합니다.</label>
      </form>

      <aside class="signup-guide">
        <div class="speech">러비와 함께<br /><strong>특별한 추억을<br />만들어보자!</strong> 💕</div>
        <img :src="raccoon" alt="손을 흔드는 러비" />
        <button type="button" :disabled="!canSubmit || submitting" data-testid="signup-submit" @click="submit">
          {{ submitting ? '가입 중…' : '회원가입 완료' }}
        </button>
        <p v-if="error" class="success" role="alert">{{ error }}</p>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.signup-page{position:relative;width:min(1040px,calc(100vw - 32px));min-height:880px;margin:18px auto;padding:70px 54px 45px;overflow:hidden;border:2px solid #f1c6bf;border-radius:30px;background:linear-gradient(rgba(255,250,245,.84),rgba(255,250,245,.84)),url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center/560px;box-shadow:0 22px 60px rgba(103,66,55,.18);color:#5e4740}.signup-page:before{content:'';position:absolute;left:32px;right:32px;top:0;height:35px;background:radial-gradient(circle at 14px 12px,#ab7067 0 6px,transparent 6.5px);background-size:52px 32px}.signup-page__burst{position:absolute;right:38px;top:54px;width:130px;opacity:.78}.back{position:absolute;left:38px;top:70px;font-size:42px;color:#775e57}.signup-head{text-align:center}.signup-head img{width:56px;height:44px;object-fit:contain}.signup-head h1{font-family:Georgia,serif;font-size:38px;color:#ed6680}.signup-head strong{display:block;margin-top:4px;font-family:Georgia,serif;font-size:21px;color:#ef7d92}.signup-head p{margin-top:10px;font-size:13px}.signup-layout{display:grid;grid-template-columns:minmax(0,560px) 1fr;gap:52px;align-items:center;margin-top:30px}.signup-form{position:relative;display:flex;flex-direction:column;gap:10px;padding:30px;background:rgba(255,253,250,.8);border:1px solid #eed7d0;border-radius:18px;box-shadow:0 8px 24px rgba(128,97,89,.12)}.signup-form__tape{position:absolute;left:-54px;top:-58px;width:145px;transform:rotate(-12deg);pointer-events:none}.signup-form input[type=email],.signup-form input[type=password],.signup-form input[type=text],.signup-form input:not([type]){width:100%;height:46px;padding:0 15px;border:1px solid #e8d7d1;border-radius:11px;background:#fff;font-size:13px}.field input{width:100%;height:44px;margin-top:6px;padding:0 13px;border:1px solid #e8d7d1;border-radius:10px;background:#fff}.sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}fieldset{border:0}legend,.field>span{margin-bottom:7px;font-size:12px;font-weight:700}.gender{display:grid;grid-template-columns:1fr 1fr;gap:10px}.gender label{display:flex;justify-content:center;gap:8px;padding:10px;border:1px solid #ead8d1;border-radius:10px;background:#fff}.gender label.selected{border-color:#f2a5b2;background:#fff0f2;color:#e75f78}.style-chips{display:flex;flex-wrap:wrap;gap:8px}.style-chips button{padding:8px 14px;border:1px solid #ead8d1;border-radius:999px;background:#fff;font-size:12px}.style-chips button.selected{border-color:#f19bad;background:#fff0f2;color:#e65e78}.agree{display:flex;align-items:center;gap:9px;margin-top:3px;font-size:11px}.agree input{width:17px;height:17px;accent-color:#ef6680}.field-error{margin:-5px 4px 0;color:#c94f63;font-size:11px}.signup-guide{text-align:center}.speech{position:relative;margin:0 auto 8px;width:210px;padding:20px;border:1px solid #f1bbb6;border-radius:16px;background:rgba(255,249,243,.88);line-height:1.6}.speech:after{content:'';position:absolute;left:50%;bottom:-9px;width:16px;height:16px;background:#fff9f3;border-right:1px solid #f1bbb6;border-bottom:1px solid #f1bbb6;transform:rotate(45deg)}.signup-guide>img{display:block;width:230px;height:230px;margin:0 auto;object-fit:contain}.signup-guide>button{width:250px;padding:14px;border-radius:999px;background:linear-gradient(90deg,#ff8ca7,#f05b79);color:#fff;font-size:15px;font-weight:800;box-shadow:0 7px 16px rgba(240,91,121,.23)}.signup-guide>button:disabled{opacity:.42;cursor:not-allowed}.success{margin:14px auto 0;max-width:270px;color:#d6536d;font-size:12px;line-height:1.6}
.signup-head strong{margin-top:1px;font-family:var(--lm-font-logo);font-size:32px;font-weight:400}.signup-head p{margin-top:7px}
@media(max-width:760px){.signup-page{min-height:0;padding:72px 20px 32px}.signup-page__burst{width:90px;right:-12px}.signup-layout{grid-template-columns:1fr;gap:25px}.signup-guide>img{width:160px;height:160px}.signup-form{padding:24px 18px}.back{left:20px}.signup-head h1{font-size:32px}.signup-head strong{font-size:28px}}
</style>

<template>
  <div class="home">
    <h1>Hify</h1>
    <p>{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get } from '../utils/request'

const message = ref('')

onMounted(async () => {
  message.value = await get<string>('/health')
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
}
</style>

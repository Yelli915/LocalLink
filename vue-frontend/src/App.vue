<template>
  <div v-if="demoState.active" class="demo-badge" role="status">데모 데이터</div>
  <router-view />
</template>

<script setup>
import { demoState } from '@/config/mock.js'
</script>

<style>
.demo-badge {
  position: fixed;
  top: 12px;
  right: 12px;
  z-index: 9999;
  padding: 4px 10px;
  border-radius: 999px;
  background: #FAEEDA;
  color: #854F0B;
  border: 1px solid #854F0B;
  font-size: 12px;
  font-weight: 600;
  pointer-events: none;
}
</style>

<template>
  <div class="page-card">
    <h3 class="section-title">站内消息</h3>
    <el-table :data="rows" border>
      <el-table-column prop="title" label="标题" width="220"/>
      <el-table-column prop="contentText" label="内容"/>
      <el-table-column prop="readFlag" label="已读" width="100">
        <template #default="scope">{{ scope.row.readFlag ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="180"/>
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button v-if="!scope.row.readFlag" type="primary" link @click="mark(scope.row.id)">标记已读</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const rows = ref([])

const load = async () => {
  const res = await http.get('/notices/my')
  rows.value = res.data || []
}
const mark = async (id) => {
  await http.post(`/notices/${id}/read`)
  await load()
}

onMounted(load)
</script>

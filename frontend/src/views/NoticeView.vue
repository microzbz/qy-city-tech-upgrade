<template>
  <div class="page-card">
    <h3 class="section-title">站内消息</h3>
    <el-table :data="rows" border>
      <el-table-column prop="title" label="标题" width="220"/>
      <el-table-column prop="contentText" label="内容"/>
      <el-table-column prop="readFlag" label="已读" width="100">
        <template #default="scope">{{ scope.row.readFlag ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button v-if="!scope.row.readFlag" type="primary" link @click="mark(scope.row.id)">标记已读</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-pager">
      <el-pagination
        v-model:current-page="pager.page"
        v-model:page-size="pager.size"
        :total="pager.total"
        :page-sizes="pageSizeOptions"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onSizeChange"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import { formatDateTime } from '../utils/datetime'

const rows = ref([])
const pageSizeOptions = [10, 20, 50, 100]
const pager = reactive({ page: 1, size: 20, total: 0 })

const load = async () => {
  const res = await http.get('/notices/my', { params: { page: pager.page, size: pager.size } })
  const data = res.data || {}
  rows.value = data.records || []
  pager.total = data.total || 0
  pager.page = data.page || pager.page
  pager.size = data.size || pager.size
}
const mark = async (id) => {
  await http.post(`/notices/${id}/read`)
  await load()
}

const onPageChange = (page) => {
  pager.page = page
  load()
}

const onSizeChange = (size) => {
  pager.page = 1
  pager.size = size
  load()
}

onMounted(load)
</script>

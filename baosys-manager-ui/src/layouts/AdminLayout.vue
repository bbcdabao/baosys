<template>
  <RoutedAdminLayout
    system-permission="MGR"
    :can="can"
    :user-name="auth.currentUser?.displayName"
    :sign-out="goToManagerSignOut"
    app-short-name="B"
  />
</template>
<script setup lang="ts">
import { RoutedAdminLayout } from '@baosys/common-ui/layout'

import { useAuthStore } from '@/store/auth'
import { goToManagerSignOut } from '@/utils/loginRedirect'
const auth = useAuthStore()
const can = (permission: string) => auth.currentUser?.permissions.includes(permission) === true
</script>

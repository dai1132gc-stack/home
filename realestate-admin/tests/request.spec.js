import { describe, expect, it } from 'vitest';
import { isTokenExpired401, shouldSkipRefresh } from '@/utils/request';
describe('request 工具', () => {
    describe('isTokenExpired401', () => {
        it('401 且无业务码视为可刷新', () => {
            expect(isTokenExpired401(401, undefined)).toBe(true);
        });
        it('401 + AUTH_40102（令牌无效/过期）视为可刷新', () => {
            expect(isTokenExpired401(401, 'AUTH_40102')).toBe(true);
        });
        it('401 + 登录失败 AUTH_40103 不可刷新', () => {
            expect(isTokenExpired401(401, 'AUTH_40103')).toBe(false);
        });
        it('500 不视为可刷新', () => {
            expect(isTokenExpired401(500, 'COMMON_50000')).toBe(false);
        });
    });
    describe('shouldSkipRefresh', () => {
        it('登录接口不触发刷新', () => {
            expect(shouldSkipRefresh('/auth/login')).toBe(true);
        });
        it('刷新接口自身不触发刷新（避免无限循环）', () => {
            expect(shouldSkipRefresh('/auth/refresh')).toBe(true);
        });
        it('业务接口可刷新', () => {
            expect(shouldSkipRefresh('/smoke/admin')).toBe(false);
        });
        it('undefined url 跳过刷新', () => {
            expect(shouldSkipRefresh(undefined)).toBe(true);
        });
    });
});

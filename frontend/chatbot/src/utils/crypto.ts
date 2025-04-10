import CryptoJS from 'crypto-js'

/**
 * 加密工具类
 */
export class CryptoUtil {
    /**
     * 使用MD5加密密码
     * @param password 原始密码
     * @returns 加密后的密码
     */
    public static encryptPassword(password: string): string {
        return CryptoJS.MD5(password).toString()
    }
} 
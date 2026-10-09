import assert from 'node:assert/strict'
import { toCouponInput, couponInputToUtc } from '../src/utils/couponTime.js'
const previousTimezone=process.env.TZ
try {
 process.env.TZ='Asia/Taipei'
 assert.equal(couponInputToUtc('2026-10-02T09:30:15.123'),'2026-10-02T01:30:15.123')
 assert.equal(couponInputToUtc('2026-10-02T09:30'),'2026-10-02T01:30:00.000')
 assert.equal(toCouponInput('2026-10-02T01:30:15.1234567'),'2026-10-02T09:30:15.123')
 assert.throws(()=>couponInputToUtc('2026-02-30T09:30'),/不存在/)
 assert.throws(()=>couponInputToUtc('2026-10-02T09:30:15X123'),/有效/)
 process.env.TZ='UTC'
 assert.equal(toCouponInput('2026-10-02T01:30:15.123'),'2026-10-02T01:30:15.123')
 assert.equal(couponInputToUtc('2026-10-02T01:30:15.123'),'2026-10-02T01:30:15.123')
 process.env.TZ='America/New_York'
 assert.throws(()=>couponInputToUtc('2026-03-08T02:30'),/不存在/)
 assert.equal(couponInputToUtc('2026-03-08T03:30'),'2026-03-08T07:30:00.000')
 console.log('PASS coupon time: Taipei, UTC, precision, invalid dates and DST gap')
} finally {
 if(previousTimezone===undefined)delete process.env.TZ
 else process.env.TZ=previousTimezone
}

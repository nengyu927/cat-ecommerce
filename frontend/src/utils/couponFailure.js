const reasons={
 COUPON_INVALID_FORMAT:'優惠碼格式不正確，請檢查後重新套用。',
 COUPON_NOT_FOUND:'找不到此優惠碼，請檢查後重新套用。',
 COUPON_INACTIVE:'此優惠券已停用，請使用其他優惠碼或移除優惠。',
 COUPON_NOT_STARTED:'此優惠券尚未開始，請稍後再試或移除優惠。',
 COUPON_EXPIRED:'此優惠券已到期，請使用其他優惠碼或移除優惠。',
 COUPON_MINIMUM_NOT_MET:'商品金額未達使用門檻，請調整購物車後重新套用，或移除優惠。',
 COUPON_EXHAUSTED:'優惠券名額已用完；套用報價不會保留名額，請使用其他優惠碼或移除優惠。',
 COUPON_ALREADY_USED:'你已使用過此優惠券，請使用其他優惠碼或移除優惠。'
}
export function isCouponFailure(error){return Object.hasOwn(reasons,error?.code)}
export function couponFailureMessage(error){return reasons[error?.code] || error?.message || '優惠報價失敗，請重新套用或移除優惠。'}

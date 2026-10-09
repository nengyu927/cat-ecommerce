// Deterministic quote contract used only by UI regression fixtures.
module.exports=(cart,discount=0,couponCode=null)=>{
 const rows=[...cart.items].sort((a,b)=>a.skuId-b.skuId)
 const goodsTotal=cart.totalAmount,shippingFee=goodsTotal>=1000?0:80
 return {couponCode,goodsTotal,discountAmount:discount,shippingFee,totalAmount:goodsTotal-discount+shippingFee,
  cartSnapshot:JSON.stringify(rows.map(i=>[i.skuId,i.quantity])),
  priceSnapshot:rows.map(i=>i.skuId+':'+i.quantity+':'+Number(i.unitPrice)).join('|')}
}

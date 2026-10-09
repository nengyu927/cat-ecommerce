// Existing checkout regressions explicitly approve the new review step.
module.exports=async page=>{
 await page.getByRole('button',{name:'確認下單',exact:true}).click()
 await page.getByRole('dialog',{name:'確認此次訂單',exact:true}).getByRole('button',{name:'確認成立訂單',exact:true}).click()
}

import { onMounted, onBeforeUnmount } from 'vue'

// One delegated listener for current and future storefront controls; no API or click interception.
export function useStorefrontFeedback() {
  const animations = new Set(), active = new WeakMap()
  let preference
  function cancelAll() { animations.forEach(a => a.cancel()); animations.clear() }
  function feedback(event) {
    if (preference?.matches || !(event.target instanceof Element)) return
    const control = event.target.closest('button, a')
    if (!control?.closest('.storefront-shell') || control.disabled || control.getAttribute('aria-disabled') === 'true') return
    if (!control.animate || control.classList.contains('skip-link')) return
    active.get(control)?.cancel()
    const transform = getComputedStyle(control).transform
    const base = transform === 'none' ? '' : transform
    const animation = control.animate([
      { transform: base + ' scale(1)' },
      { transform: base + ' scale(.96)', offset: .3 },
      { transform: base + ' scale(1)' }
    ], { duration: 240, easing: 'cubic-bezier(.2,.8,.2,1)' })
    active.set(control, animation); animations.add(animation)
    animation.finished.catch(() => {}).finally(() => {
      animations.delete(animation)
      if (active.get(control) === animation) active.delete(control)
    })
  }
  function changed() { if (preference.matches) cancelAll() }
  onMounted(() => {
    preference = matchMedia('(prefers-reduced-motion: reduce)')
    preference.addEventListener('change', changed)
    document.addEventListener('click', feedback, true)
  })
  onBeforeUnmount(() => {
    document.removeEventListener('click', feedback, true)
    preference?.removeEventListener('change', changed)
    cancelAll()
  })
}

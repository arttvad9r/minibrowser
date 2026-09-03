# applyChromiumBottomSheetMotion (ui/Motion.kt) выставляет library-owned motion specs
# SheetState по имени поля. Без этого правила R8 переименовывает их (showMotionSpec -> e,
# hideMotionSpec -> f, anchoredDraggableMotionSpec -> c), и Chromium-тайминг молча
# не применяется только в release. Сами поля использует Material3, шринкинг их не удалит.
-keepclassmembernames class androidx.compose.material3.SheetState {
    androidx.compose.animation.core.FiniteAnimationSpec showMotionSpec;
    androidx.compose.animation.core.FiniteAnimationSpec hideMotionSpec;
    androidx.compose.animation.core.AnimationSpec anchoredDraggableMotionSpec;
}

# BYD API Probe

独立、只读的 BYD 车辆统计 API 可行性探测工具，不参与 CarTimer 正式 App 构建。

探测内容：

- `BYDAutoStatisticDevice` 类是否可见；
- `BYDAUTO_STATISTIC_GET` 权限状态；
- `getInstance(Context)` 是否成功；
- `getTotalMileageValue()` 原始返回值。

Probe 使用 reflection，不保存 BYD framework JAR，不调用任何车辆控制 setter，也不写数据库或修改 CarTimer 状态。

构建：

```text
./gradlew -p tools/byd-api-probe :app:assembleDebug
```

单位必须通过车机仪表盘对照确认，不能将 raw value 直接解释为公里或米。

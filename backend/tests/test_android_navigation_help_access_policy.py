from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ROUTES = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "navigation" / "Routes.kt"
APP_NAV_HOST = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "navigation" / "AppNavHost.kt"
DASHBOARD_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "DashboardRefinedScreen.kt"
BOTTOM_NAV_BAR = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "components" / "BottomNavBar.kt"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_help_and_methodology_routes_are_not_plan_gated() -> None:
    routes_source = _read(ROUTES)
    assert "val MoreTools = defineRoute(\"more_tools\", RouteAccess.GuidedCore)" in routes_source
    assert "val Ipo = defineRoute(\"ipo\", RouteAccess.GuidedCore)" in routes_source


def test_bottom_nav_keeps_methodology_tab_available_before_first_plan() -> None:
    nav_source = _read(APP_NAV_HOST)
    assert "base.remove(Routes.GroceryList)" in nav_source
    assert "base.remove(Routes.Progress)" in nav_source
    assert "base.remove(Routes.Ipo)" not in nav_source


def test_dashboard_uses_shared_header_without_duplicate_notification_shortcut() -> None:
    dashboard_source = _read(DASHBOARD_SCREEN)
    nav_source = _read(APP_NAV_HOST)

    assert "SharedTopHeader(" in dashboard_source
    assert "onNotifications" not in dashboard_source
    assert "RefinedIconAction(" not in dashboard_source
    assert "onOpenNotifications =" not in nav_source


def test_bottom_navigation_uses_product_facing_support_label() -> None:
    nav_source = _read(BOTTOM_NAV_BAR)
    assert "BottomNavItem(route = Routes.Ipo, label = \"Support\"" in nav_source
    assert "label = \"Community\"" not in nav_source
    assert "label = \"IPO\"" not in nav_source

package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.BhoomgaadiApp
import com.example.ui.screens.billing.CreateBillViewModel
import com.example.ui.screens.billing.CreateBillWizardScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.export.ExportScreen
import com.example.ui.screens.farmer.FarmerFormScreen
import com.example.ui.screens.farmer.FarmerSearchScreen
import com.example.ui.screens.farmer.FarmerViewModel
import com.example.ui.screens.history.BillDetailScreen
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.history.ProcurementHistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel

object Routes {
    const val HOME = "home"
    const val FARMERS = "farmers"
    const val FARMER_FORM = "farmer_form?farmerUuid={farmerUuid}&aadhaar={aadhaar}"
    const val CREATE_BILL = "create_bill?farmerUuid={farmerUuid}"
    const val HISTORY = "history"
    const val BILL_DETAIL = "bill_detail/{billUuid}"
    const val DASHBOARD = "dashboard"
    const val EXPORT = "export"
    const val SETTINGS = "settings"

    fun farmerForm(farmerUuid: String = "", aadhaar: String = ""): String =
        "farmer_form?farmerUuid=$farmerUuid&aadhaar=$aadhaar"

    fun createBill(farmerUuid: String = ""): String =
        "create_bill?farmerUuid=$farmerUuid"

    fun billDetail(billUuid: String): String = "bill_detail/$billUuid"
}

@Composable
fun BhoomgaadiNavHost(app: BhoomgaadiApp) {
    val navController = rememberNavController()

    val farmerVm: FarmerViewModel = viewModel(factory = FarmerViewModel.Factory(app.repository))
    val createBillVm: CreateBillViewModel = viewModel(factory = CreateBillViewModel.Factory(app.repository))
    val historyVm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(app.repository))
    val dashboardVm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(app.repository))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.repository))

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                prefs = app.appPreferences,
                dashboardViewModel = dashboardVm,
                historyViewModel = historyVm,
                onCreateBill = {
                    createBillVm.resetWizard()
                    navController.navigate(Routes.createBill())
                },
                onOpenFarmers = { navController.navigate(Routes.FARMERS) },
                onAddFarmer = { navController.navigate(Routes.farmerForm()) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) },
                onOpenExport = { navController.navigate(Routes.EXPORT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenBillDetail = { uuid -> navController.navigate(Routes.billDetail(uuid)) }
            )
        }

        composable(Routes.FARMERS) {
            FarmerSearchScreen(
                viewModel = farmerVm,
                onBack = { navController.popBackStack() },
                onAddFarmer = { aadhaar -> navController.navigate(Routes.farmerForm(aadhaar = aadhaar)) },
                onEditFarmer = { uuid -> navController.navigate(Routes.farmerForm(farmerUuid = uuid)) },
                onCreateBillForFarmer = { uuid ->
                    createBillVm.resetWizard()
                    navController.navigate(Routes.createBill(farmerUuid = uuid))
                }
            )
        }

        composable(
            route = Routes.FARMER_FORM,
            arguments = listOf(
                navArgument("farmerUuid") { type = NavType.StringType; defaultValue = "" },
                navArgument("aadhaar") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val farmerUuid = entry.arguments?.getString("farmerUuid").orEmpty()
            val aadhaar = entry.arguments?.getString("aadhaar").orEmpty()
            LaunchedEffect(farmerUuid, aadhaar) {
                if (farmerUuid.isNotBlank()) {
                    farmerVm.loadFarmerForEdit(farmerUuid)
                } else {
                    farmerVm.startNewFarmer(aadhaar)
                }
            }
            FarmerFormScreen(
                viewModel = farmerVm,
                onBack = { navController.popBackStack() },
                onSaved = { savedUuid ->
                    // If user came from Create Bill wizard, auto-select the newly created farmer
                    val prevRoute = navController.previousBackStackEntry?.destination?.route
                    if (prevRoute == Routes.CREATE_BILL) {
                        createBillVm.preselectFarmerById(savedUuid)
                    }
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.CREATE_BILL,
            arguments = listOf(
                navArgument("farmerUuid") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val preselectedFarmerUuid = entry.arguments?.getString("farmerUuid").orEmpty()
            CreateBillWizardScreen(
                viewModel = createBillVm,
                preselectedFarmerUuid = preselectedFarmerUuid,
                onBack = { navController.popBackStack() },
                onQuickAddFarmer = { navController.navigate(Routes.farmerForm()) },
                onBillCreated = { billUuid ->
                    navController.navigate(Routes.billDetail(billUuid)) {
                        popUpTo(Routes.CREATE_BILL) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HISTORY) {
            ProcurementHistoryScreen(
                viewModel = historyVm,
                onBack = { navController.popBackStack() },
                onCreateBill = {
                    createBillVm.resetWizard()
                    navController.navigate(Routes.createBill())
                },
                onOpenBillDetail = { uuid -> navController.navigate(Routes.billDetail(uuid)) }
            )
        }

        composable(
            route = Routes.BILL_DETAIL,
            arguments = listOf(navArgument("billUuid") { type = NavType.StringType })
        ) { entry ->
            val billUuid = entry.arguments?.getString("billUuid").orEmpty()
            BillDetailScreen(
                billUuid = billUuid,
                viewModel = historyVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.EXPORT) {
            ExportScreen(
                prefs = app.appPreferences,
                csvExportManager = app.csvExportManager,
                backupManager = app.backupManager,
                dashboardViewModel = dashboardVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

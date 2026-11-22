package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.BhoomgaadiApp
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.billing.CreateBillViewModel
import com.example.ui.screens.billing.CreateBillWizardScreen
import com.example.ui.screens.billing.ReceiptPreviewScreen
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
    const val CREATE_BILL = "create_bill?farmerUuid={farmerUuid}&editBillUuid={editBillUuid}"
    const val RECEIPT_PREVIEW = "receipt_preview/{billUuid}"
    const val HISTORY = "history"
    const val BILL_DETAIL = "bill_detail/{billUuid}"
    const val DASHBOARD = "dashboard"
    const val EXPORT = "export"
    const val SETTINGS = "settings"

    fun farmerForm(farmerUuid: String = "", aadhaar: String = ""): String =
        "farmer_form?farmerUuid=$farmerUuid&aadhaar=$aadhaar"

    fun createBill(farmerUuid: String = "", editBillUuid: String = ""): String =
        "create_bill?farmerUuid=$farmerUuid&editBillUuid=$editBillUuid"

    fun receiptPreview(billUuid: String): String = "receipt_preview/$billUuid"

    fun billDetail(billUuid: String): String = "bill_detail/$billUuid"
}

@Composable
fun BhoomgaadiNavHost(
    app: BhoomgaadiApp,
    authViewModel: AuthViewModel
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val language by authViewModel.language.collectAsStateWithLifecycle()
    val currentUser = authState.currentUser

    // Gate entire app behind offline Admin/Officer PIN login (or first-run Admin Setup)
    if (currentUser == null) {
        LoginScreen(authViewModel = authViewModel)
        return
    }

    val navController = rememberNavController()

    val farmerVm: FarmerViewModel = viewModel(factory = FarmerViewModel.Factory(app.repository))
    val createBillVm: CreateBillViewModel = viewModel(factory = CreateBillViewModel.Factory(app.repository))
    val historyVm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(app.repository))
    val dashboardVm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(app.repository))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.repository))

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                currentUser = currentUser,
                currentLanguage = language,
                onToggleLanguage = authViewModel::toggleLanguage,
                onSearchFarmer = {
                    farmerVm.resetAadhaarLookup()
                    navController.navigate(Routes.FARMERS)
                },
                onAddFarmer = { navController.navigate(Routes.farmerForm()) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenExport = { navController.navigate(Routes.EXPORT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onLogout = { authViewModel.logout() }
            )
        }

        composable(Routes.FARMERS) {
            FarmerSearchScreen(
                viewModel = farmerVm,
                onBack = { navController.popBackStack() },
                onAddFarmer = { aadhaar -> navController.navigate(Routes.farmerForm(aadhaar = aadhaar)) },
                onEditFarmer = { uuid -> navController.navigate(Routes.farmerForm(farmerUuid = uuid)) },
                onCreateBillForFarmer = { uuid ->
                    createBillVm.startNewBill(preselectedFarmerUuid = uuid)
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
                currentUser = currentUser,
                onBack = { navController.popBackStack() },
                onCreateBillForFarmer = { savedUuid ->
                    createBillVm.startNewBill(preselectedFarmerUuid = savedUuid)
                    navController.navigate(Routes.createBill(farmerUuid = savedUuid)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onGoHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(
            route = Routes.CREATE_BILL,
            arguments = listOf(
                navArgument("farmerUuid") { type = NavType.StringType; defaultValue = "" },
                navArgument("editBillUuid") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val farmerUuid = entry.arguments?.getString("farmerUuid").orEmpty()
            val editBillUuid = entry.arguments?.getString("editBillUuid").orEmpty()

            LaunchedEffect(farmerUuid, editBillUuid) {
                if (editBillUuid.isNotBlank()) {
                    createBillVm.startEditExistingBill(editBillUuid)
                } else if (farmerUuid.isNotBlank()) {
                    createBillVm.preselectFarmerById(farmerUuid)
                }
            }

            CreateBillWizardScreen(
                viewModel = createBillVm,
                currentUser = currentUser,
                onBackToHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onQuickAddFarmer = { prefillAadhaar ->
                    navController.navigate(Routes.farmerForm(aadhaar = prefillAadhaar))
                },
                onEditFarmer = { fUuid ->
                    navController.navigate(Routes.farmerForm(farmerUuid = fUuid))
                },
                onOpenReceiptPreview = { billUuid ->
                    navController.navigate(Routes.receiptPreview(billUuid))
                }
            )
        }

        composable(
            route = Routes.RECEIPT_PREVIEW,
            arguments = listOf(navArgument("billUuid") { type = NavType.StringType })
        ) { entry ->
            val billUuid = entry.arguments?.getString("billUuid").orEmpty()
            ReceiptPreviewScreen(
                billUuid = billUuid,
                historyViewModel = historyVm,
                onClose = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            ProcurementHistoryScreen(
                viewModel = historyVm,
                onBack = { navController.popBackStack() },
                onCreateBill = {
                    farmerVm.resetAadhaarLookup()
                    navController.navigate(Routes.FARMERS)
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
                currentUser = currentUser,
                viewModel = historyVm,
                onBack = { navController.popBackStack() },
                onEditBill = { targetBillUuid ->
                    navController.navigate(Routes.createBill(editBillUuid = targetBillUuid))
                },
                onOpenReceiptPreview = { targetBillUuid ->
                    navController.navigate(Routes.receiptPreview(targetBillUuid))
                }
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
                currentUser = currentUser,
                prefs = app.appPreferences,
                repository = app.repository,
                csvExportManager = app.csvExportManager,
                backupManager = app.backupManager,
                dashboardViewModel = dashboardVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                currentUser = currentUser,
                viewModel = settingsVm,
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

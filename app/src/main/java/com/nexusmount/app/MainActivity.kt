package com.nexusmount.app

import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.nexusmount.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout
        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        // Bottom nav: navigate even desde pantallas profundas (SMB, etc.)
        binding.bottomNav.setOnItemSelectedListener { item ->
            val opts = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.dashboardFragment, false)
                .build()
            try {
                navController.navigate(item.itemId, null, opts)
                true
            } catch (_: Exception) {
                try {
                    navController.navigate(item.itemId)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        navController.addOnDestinationChangedListener { _, dest, _ ->
            val id = dest.id
            val bottomIds = setOf(
                R.id.dashboardFragment, R.id.drivesFragment, R.id.filesFragment,
                R.id.transfersFragment, R.id.settingsFragment
            )
            if (id in bottomIds) {
                binding.bottomNav.menu.findItem(id)?.isChecked = true
            }
        }

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.toolbar,
            R.string.app_name, R.string.app_name
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener { item ->
            val opts = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.dashboardFragment, false)
                .build()
            try {
                navController.navigate(item.itemId, null, opts)
            } catch (_: Exception) {
                try {
                    navController.navigate(item.itemId)
                } catch (_: Exception) {
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

package com.example.project_mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.project_mobile.R
import com.example.project_mobile.data.BarberService
import com.example.project_mobile.data.Barber
import com.example.project_mobile.ui.viewmodel.BookingViewModel
import com.example.project_mobile.ui.utils.ImageMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNextClick: () -> Unit,
    viewModel: BookingViewModel = viewModel()
) {
    val selectedBarber by viewModel.selectedBarber.collectAsStateWithLifecycle()
    val selectedService by viewModel.selectedService.collectAsStateWithLifecycle()
    val selectedTime by viewModel.selectedTime.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val allBookings by viewModel.allBookings.collectAsStateWithLifecycle()
    val currentUserId by viewModel.userId.collectAsStateWithLifecycle()
    
    val barbers by viewModel.barbers.collectAsStateWithLifecycle()
    val availableServices by viewModel.availableServices.collectAsStateWithLifecycle()
    val isLoadingBarbers by viewModel.isLoadingBarbers.collectAsStateWithLifecycle()
    val isLoadingServices by viewModel.isLoadingServices.collectAsStateWithLifecycle()
    
    val times = viewModel.times
    val dateList = remember { (0..14).map { LocalDate.now().plusDays(it.toLong()) } }

    // Optimization: Use derivedStateOf for complex UI state calculations
    val bookedSlots by remember(allBookings, selectedBarber, selectedDate) {
        derivedStateOf {
            if (selectedBarber == null || selectedDate == null) emptySet<String>()
            else {
                val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                val dateStr = selectedDate?.format(dateFormatter)
                allBookings
                    .filter { it.barberName == selectedBarber && it.date == dateStr }
                    .map { it.time }
                    .toSet()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.barber_shop), 
                            contentDescription = null, 
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "OREO BARBER",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle("Choose Your Barber")
            if (isLoadingBarbers) {
                LoadingItem()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    barbers.forEach { barber ->
                        BarberItemUI(
                            barber = barber,
                            isSelected = selectedBarber == barber.name,
                            onClick = { viewModel.selectBarber(barber.name) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Select Service")
            if (isLoadingServices) {
                LoadingItem()
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableServices, key = { it.id }) { service ->
                        ServiceItem(
                            service = service,
                            isSelected = selectedService?.id == service.id,
                            onClick = { viewModel.selectService(service) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Select Date")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(dateList) { date ->
                    DateItem(
                        date = date,
                        isSelected = selectedDate == date,
                        onClick = { viewModel.selectDate(date) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Select Available Time")
            
            // Optimization: Avoid heavy calculations inside the UI loop
            val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH) }
            val dateStr = selectedDate?.format(dateFormatter)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                times.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { time ->
                            val isBooked = bookedSlots.contains(time)
                            val isMine = allBookings.any { 
                                it.barberName == selectedBarber && it.date == dateStr && it.time == time && it.userId == currentUserId 
                            }
                            
                            Box(modifier = Modifier.weight(1f)) {
                                TimeSlot(
                                    time = time,
                                    isSelected = selectedTime == time,
                                    isAvailable = !isBooked && selectedDate != null,
                                    isMine = isMine,
                                    onClick = { viewModel.selectTime(time) }
                                )
                            }
                        }
                        if (row.size < 3) repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onNextClick,
                enabled = selectedBarber != null && selectedService != null && selectedTime != null && selectedDate != null,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("NEXT STEP", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title, 
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
}

@Composable
fun LoadingItem() {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

@Composable
fun ServiceItem(service: BarberService, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.width(140.dp).padding(16.dp)) {
            Text(
                service.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachMoney, null, modifier = Modifier.size(16.dp), tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                Text(service.price, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(16.dp), tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(service.duration, style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BarberItemUI(barber: Barber, isSelected: Boolean, onClick: () -> Unit) {
    val imageRes = ImageMapper.getBarberImageRes(barber)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (imageRes != 0) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = barber.name,
                    modifier = Modifier.size(56.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Text(barber.name.take(1), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(barber.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(barber.specialty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("★ ${barber.rating}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            RadioButton(selected = isSelected, onClick = null)
        }
    }
}

@Composable
fun DateItem(date: LocalDate, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.width(68.dp).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.format(DateTimeFormatter.ofPattern("MMM")), style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun TimeSlot(time: String, isSelected: Boolean, isAvailable: Boolean, isMine: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = isAvailable || isMine,
        shape = RoundedCornerShape(16.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isMine -> MaterialTheme.colorScheme.primaryContainer
            !isAvailable -> Color.Transparent
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, if (isSelected || isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAvailable) 1f else 0.3f))
    ) {
        Box(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = time,
                fontWeight = FontWeight.Bold,
                color = when {
                    isSelected -> Color.White
                    isMine -> MaterialTheme.colorScheme.onPrimaryContainer
                    !isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

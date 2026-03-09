package com.example.project_mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.project_mobile.R
import com.example.project_mobile.data.Booking
import com.example.project_mobile.data.Barber
import com.example.project_mobile.ui.viewmodel.BookingViewModel
import com.example.project_mobile.ui.utils.ImageMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: BookingViewModel = viewModel()
) {
    var bookingToCancel by remember { mutableStateOf<Booking?>(null) }
    var editingBooking by remember { mutableStateOf<Booking?>(null) }
    
    val bookings by viewModel.bookings.collectAsStateWithLifecycle()
    val allBookings by viewModel.allBookings.collectAsStateWithLifecycle()
    val barbers by viewModel.barbers.collectAsStateWithLifecycle()

    // Optimization: Associate barbers by name for O(1) lookup in the list
    val barberMap by remember(barbers) { 
        derivedStateOf { barbers.associateBy { it.name } } 
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
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

        Text(
            "Booking History", 
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (bookings.isEmpty()) {
            EmptyHistoryView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Optimization: Provide a unique key for list items
                items(bookings, key = { it.id }) { booking ->
                    HistoryItem(
                        booking = booking,
                        barber = barberMap[booking.barberName],
                        onCancelClick = { bookingToCancel = booking },
                        onEditClick = { editingBooking = booking }
                    )
                }
            }
        }
    }

    if (bookingToCancel != null) {
        CancelConfirmationDialog(
            onConfirm = {
                bookingToCancel?.let { viewModel.cancelBooking(it) }
                bookingToCancel = null
            },
            onDismiss = { bookingToCancel = null }
        )
    }

    if (editingBooking != null) {
        EditBookingDialog(
            booking = editingBooking!!,
            onDismiss = { editingBooking = null },
            onSave = { updatedBooking ->
                viewModel.updateBooking(updatedBooking)
                editingBooking = null
            },
            allBookings = allBookings,
            barbers = barbers,
            times = viewModel.times
        )
    }
}

@Composable
fun EmptyHistoryView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CalendarMonth, 
                contentDescription = null, 
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No bookings found", 
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CancelConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Cancel Booking?") },
        text = { Text("Are you sure? This action cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Yes, Cancel")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("No, Keep it") } },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun HistoryItem(
    booking: Booking, 
    barber: Barber?, 
    onCancelClick: () -> Unit, 
    onEditClick: () -> Unit
) {
    val imageRes = ImageMapper.getBarberImageRes(barber)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarberAvatar(imageRes, booking.barberName)
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(booking.barberName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(booking.serviceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("${booking.date} • ${booking.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = booking.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (booking.status == "Confirmed") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            
            IconButton(onClick = onEditClick) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onCancelClick) { Icon(Icons.Default.DeleteOutline, "Cancel", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun BarberAvatar(imageRes: Int, name: String) {
    if (imageRes != 0) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EditBookingDialog(
    booking: Booking,
    onDismiss: () -> Unit,
    onSave: (Booking) -> Unit,
    allBookings: List<Booking>,
    barbers: List<Barber>,
    times: List<String>
) {
    var selectedBarber by remember { mutableStateOf(booking.barberName) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH) }
    
    val initialDate = remember(booking.date) { 
        try { LocalDate.parse(booking.date, dateFormatter) } catch (e: Exception) { LocalDate.now() } 
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedTime by remember { mutableStateOf(booking.time) }
    
    val dateList = remember { (0..14).map { LocalDate.now().plusDays(it.toLong()) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Edit Your Booking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Barber")
                barbers.forEach { barber ->
                    BarberSelectorItem(
                        barber = barber, 
                        isSelected = selectedBarber == barber.name,
                        onClick = { selectedBarber = barber.name }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Date")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(dateList) { date ->
                        DateSelectorItem(date, selectedDate == date) { 
                            selectedDate = date
                            selectedTime = "" 
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Time")
                val dateStr = selectedDate.format(dateFormatter)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { time ->
                                val isBooked = allBookings.any { 
                                    it.id != booking.id && it.barberName == selectedBarber && it.date == dateStr && it.time == time 
                                }
                                TimeSelectorItem(time, selectedTime == time, !isBooked) { selectedTime = time }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { onSave(booking.copy(barberName = selectedBarber, date = selectedDate.format(dateFormatter), time = selectedTime)) },
                        enabled = selectedTime.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
}

@Composable
fun BarberSelectorItem(barber: Barber, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BarberAvatar(ImageMapper.getBarberImageRes(barber), barber.name)
            Text(barber.name, modifier = Modifier.padding(start = 12.dp).weight(1f), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            RadioButton(selected = isSelected, onClick = null)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun DateSelectorItem(date: LocalDate, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.width(64.dp).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.format(DateTimeFormatter.ofPattern("MMM")), style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun TimeSelectorItem(time: String, isSelected: Boolean, isAvailable: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = isAvailable,
        modifier = Modifier.width(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAvailable) 1f else 0.3f))
    ) {
        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(time, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

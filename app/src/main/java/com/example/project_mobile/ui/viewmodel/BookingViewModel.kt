package com.example.project_mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project_mobile.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BookingViewModel : ViewModel() {

    private val _userId = MutableStateFlow<String?>(FirebaseManager.getCurrentUserId())
    val userId = _userId.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    // Optimized: Combine loading states if needed or keep separate for granular UI feedback
    private val _isLoadingBarbers = MutableStateFlow(true)
    val isLoadingBarbers = _isLoadingBarbers.asStateFlow()

    private val _isLoadingServices = MutableStateFlow(true)
    val isLoadingServices = _isLoadingServices.asStateFlow()

    // Flows with standard SharingStarted configuration
    val barbers: StateFlow<List<Barber>> = FirebaseManager.getBarbersFlow()
        .onEach { _isLoadingBarbers.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableServices: StateFlow<List<BarberService>> = FirebaseManager.getServicesFlow()
        .onEach { _isLoadingServices.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val promotions: StateFlow<List<Promotion>> = FirebaseManager.getPromotionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val times = listOf("9:00", "9:30", "10:00", "10:30", "11:00", "11:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30")

    private val _selectedBarber = MutableStateFlow<String?>(null)
    val selectedBarber = _selectedBarber.asStateFlow()

    private val _selectedService = MutableStateFlow<BarberService?>(null)
    val selectedService = _selectedService.asStateFlow()

    private val _selectedTime = MutableStateFlow<String?>(null)
    val selectedTime = _selectedTime.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate = _selectedDate.asStateFlow()

    // Optimized: Use stateIn for real-time global availability
    val allBookings: StateFlow<List<Booking>> = FirebaseManager.getAllBookingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookings: StateFlow<List<Booking>> = _userId
        .flatMapLatest { uid ->
            if (uid != null) FirebaseManager.getBookingsFlow(uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            FirebaseManager.initializeAppData()
            // Sync profile on init if user exists
            _userId.value?.let { uid ->
                _userProfile.value = FirebaseManager.getUserProfile(uid)
            }
        }
    }

    fun setUserData(uid: String?, profile: UserProfile?) {
        _userId.value = uid
        _userProfile.value = profile
    }

    fun selectBarber(barber: String) {
        _selectedBarber.value = barber
        _selectedTime.value = null // Reset time when barber changes
    }

    fun selectService(service: BarberService) {
        _selectedService.value = service
    }

    fun selectTime(time: String) {
        _selectedTime.value = time
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _selectedTime.value = null // Reset time when date changes
    }

    fun confirmBooking(onSuccess: () -> Unit) {
        val uid = _userId.value ?: return
        if (_isProcessing.value) return

        val profile = _userProfile.value
        val barber = _selectedBarber.value ?: return
        val service = _selectedService.value ?: return
        val time = _selectedTime.value ?: return
        val date = _selectedDate.value ?: return

        val dateStr = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

        viewModelScope.launch {
            _isProcessing.value = true
            val booking = Booking(
                userId = uid,
                barberName = barber,
                serviceName = service.name,
                price = service.price,
                date = dateStr,
                time = time,
                userName = profile?.name ?: "Guest",
                userPhone = profile?.phone ?: "",
                userEmail = profile?.email ?: "",
                timestamp = System.currentTimeMillis()
            )
            
            FirebaseManager.saveBooking(booking)
                .onSuccess {
                    clearSelection()
                    onSuccess()
                }
            _isProcessing.value = false
        }
    }

    private fun clearSelection() {
        _selectedBarber.value = null
        _selectedService.value = null
        _selectedTime.value = null
        _selectedDate.value = null
    }

    fun cancelBooking(booking: Booking) {
        viewModelScope.launch {
            FirebaseManager.deleteBooking(booking)
        }
    }

    fun updateBooking(booking: Booking) {
        viewModelScope.launch {
            FirebaseManager.saveBooking(booking)
        }
    }

    fun submitReview(barberName: String, rating: Int, comment: String) {
        val uid = _userId.value ?: return
        val profile = _userProfile.value
        viewModelScope.launch {
            FirebaseManager.saveReview(Review(
                userId = uid,
                userName = profile?.name ?: "Guest",
                barberName = barberName,
                rating = rating,
                comment = comment
            ))
        }
    }
}

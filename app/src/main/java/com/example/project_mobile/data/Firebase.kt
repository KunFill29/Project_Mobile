package com.example.project_mobile.data

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

// --- 1. Optimized Data Models (Immutability & Property Mapping) ---

@Immutable
data class Booking(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("userId") val userId: String = "",
    @get:PropertyName("barberName") val barberName: String = "",
    @get:PropertyName("serviceName") val serviceName: String = "",
    @get:PropertyName("price") val price: String = "",
    @get:PropertyName("date") val date: String = "",
    @get:PropertyName("time") val time: String = "",
    @get:PropertyName("userName") val userName: String = "",
    @get:PropertyName("userPhone") val userPhone: String = "",
    @get:PropertyName("userEmail") val userEmail: String = "",
    @get:PropertyName("status") val status: String = "Confirmed",
    @get:PropertyName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class UserProfile(
    @get:PropertyName("uid") val uid: String = "",
    @get:PropertyName("name") val name: String = "",
    @get:PropertyName("email") val email: String = "",
    @get:PropertyName("phone") val phone: String = ""
)

@Immutable
data class Barber(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("name") val name: String = "",
    @get:PropertyName("image") val image: String = "",
    @get:PropertyName("specialty") val specialty: String = "Master Barber",
    @get:PropertyName("rating") val rating: Double = 5.0
)

@Immutable
data class BarberService(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("name") val name: String = "",
    @get:PropertyName("price") val price: String = "",
    @get:PropertyName("duration") val duration: String = "30 min"
)

@Immutable
data class Review(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("userId") val userId: String = "",
    @get:PropertyName("userName") val userName: String = "",
    @get:PropertyName("barberName") val barberName: String = "",
    @get:PropertyName("rating") val rating: Int = 5,
    @get:PropertyName("comment") val comment: String = "",
    @get:PropertyName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class Promotion(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("title") val title: String = "",
    @get:PropertyName("description") val description: String = "",
    @get:PropertyName("discountCode") val discountCode: String = "",
    @get:PropertyName("discountPercentage") val discountPercentage: Int = 0,
    @get:PropertyName("expiryDate") val expiryDate: String = ""
)

// --- 2. Firebase Manager ---

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
    private object Collections {
        const val BOOKINGS = "bookings"
        const val USERS = "users"
        const val REVIEWS = "reviews"
        const val PROMOTIONS = "promotions"
        const val BARBERS = "barbers"
        const val SERVICES = "services"
        const val SLOTS = "slots"
    }

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore
    
    private val bookingsCollection get() = db.collection(Collections.BOOKINGS)
    private val usersCollection get() = db.collection(Collections.USERS)
    private val reviewsCollection get() = db.collection(Collections.REVIEWS)
    private val promosCollection get() = db.collection(Collections.PROMOTIONS)
    private val barbersCollection get() = db.collection(Collections.BARBERS)
    private val servicesCollection get() = db.collection(Collections.SERVICES)
    private val slotsCollection get() = db.collection(Collections.SLOTS)

    // Auth Operations
    fun getCurrentUserId(): String? = auth.currentUser?.uid
    fun getCurrentUser() = auth.currentUser
    
    suspend fun signIn(email: String, password: String): Result<String> = try {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        Result.success(result.user?.uid ?: "")
    } catch (e: Exception) {
        Log.e(TAG, "Sign in error", e)
        Result.failure(e)
    }

    suspend fun signUp(email: String, password: String, name: String, phone: String): Result<String> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: throw Exception("User creation failed")
        val uid = user.uid
        
        try {
            saveUserProfile(UserProfile(uid, name, email, phone))
            Result.success(uid)
        } catch (e: Exception) {
            user.delete().await() // Rollback Auth if DB profile fails
            throw e
        }
    } catch (e: Exception) {
        Log.e(TAG, "Sign up error", e)
        Result.failure(e)
    }

    fun signOut() = auth.signOut()

    // Data Management
    suspend fun initializeAppData() {
        try {
            val defaultBarbers = listOf(
                Barber(name = "John Wick", image = "johnwick", specialty = "Skin Fade Expert", rating = 5.0),
                Barber(name = "Donald Trump", image = "trump", specialty = "Master Barber", rating = 4.9),
                Barber(name = "Dominic Toretto", image = "dominic", specialty = "Hair Stylist", rating = 4.8)
            )

            for (barber in defaultBarbers) {
                val query = barbersCollection.whereEqualTo("name", barber.name).get().await()
                if (query.isEmpty) {
                    val doc = barbersCollection.document()
                    doc.set(barber.copy(id = doc.id)).await()
                } else {
                    val docId = query.documents.first().id
                    barbersCollection.document(docId).update(
                        "image", barber.image,
                        "specialty", barber.specialty,
                        "rating", barber.rating
                    ).await()
                }
            }

            val servicesSnapshot = servicesCollection.get().await()
            if (servicesSnapshot.isEmpty) {
                db.runBatch { batch ->
                    val defaultServices = listOf(
                        BarberService(name = "Classic Haircut", price = "25", duration = "30 min"),
                        BarberService(name = "Skin Fade", price = "30", duration = "45 min"),
                        BarberService(name = "Beard Grooming", price = "15", duration = "20 min"),
                        BarberService(name = "Luxury Shave", price = "35", duration = "40 min"),
                        BarberService(name = "Full Package", price = "55", duration = "75 min")
                    )
                    defaultServices.forEach { service ->
                        val doc = servicesCollection.document()
                        batch.set(doc, service.copy(id = doc.id))
                    }
                }.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing database", e)
        }
    }

    suspend fun saveBooking(booking: Booking): Result<String> = try {
        val slotId = "${booking.barberName}_${booking.date}_${booking.time}".replace(" ", "_")
        val slotRef = slotsCollection.document(slotId)
        
        val resultId = db.runTransaction { transaction ->
            // 1. Verify Slot Availability with Stale Data Protection
            val slotSnapshot = transaction.get(slotRef)
            val currentBookingIdInSlot = slotSnapshot.getString("bookingId")
            
            if (slotSnapshot.exists() && !currentBookingIdInSlot.isNullOrEmpty()) {
                // If the slot is claimed by another booking, verify if that booking actually exists
                if (booking.id.isEmpty() || currentBookingIdInSlot != booking.id) {
                    val otherBookingSnapshot = transaction.get(bookingsCollection.document(currentBookingIdInSlot))
                    if (otherBookingSnapshot.exists()) {
                        val otherBooking = otherBookingSnapshot.toObject(Booking::class.java)
                        val otherBookingSlotId = "${otherBooking?.barberName}_${otherBooking?.date}_${otherBooking?.time}".replace(" ", "_")
                        
                        // Truly booked only if the other booking exists and still refers to this exact slot
                        if (otherBookingSlotId == slotId) {
                            throw Exception("This slot is already booked!")
                        }
                    }
                    // If the other booking doesn't exist (stale slot) or has moved, we can proceed
                }
            }

            val docRef = if (booking.id.isEmpty()) bookingsCollection.document() else bookingsCollection.document(booking.id)
            
            // 2. Update Cleanup: If editing, remove the pointer from the old slot if it changed
            if (booking.id.isNotEmpty()) {
                val oldBookingSnapshot = transaction.get(docRef)
                if (oldBookingSnapshot.exists()) {
                    val oldBooking = oldBookingSnapshot.toObject(Booking::class.java)
                    if (oldBooking != null) {
                        val oldSlotId = "${oldBooking.barberName}_${oldBooking.date}_${oldBooking.time}".replace(" ", "_")
                        if (oldSlotId != slotId) {
                            transaction.delete(slotsCollection.document(oldSlotId))
                        }
                    }
                }
            }

            val finalBooking = if (booking.id.isEmpty()) booking.copy(id = docRef.id) else booking
            
            // 3. Atomically update booking and set the new slot
            transaction.set(docRef, finalBooking)
            transaction.set(slotRef, mapOf("bookingId" to docRef.id))
            docRef.id
        }.await()
        
        Result.success(resultId)
    } catch (e: Exception) {
        Log.e(TAG, "Error saving booking", e)
        Result.failure(e)
    }

    // --- Generic Flow Helper to Reduce Boilerplate ---
    private inline fun <reified T : Any> Query.asFlow(
        crossinline transform: (List<T>) -> List<T> = { it },
        crossinline mapper: (DocumentSnapshot) -> T?
    ): Flow<List<T>> = callbackFlow {
        val subscription = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { mapper(it) } ?: emptyList()
            trySend(transform(items))
        }
        awaitClose { subscription.remove() }
    }.flowOn(Dispatchers.IO)

    fun getBookingsFlow(userId: String): Flow<List<Booking>> = 
        bookingsCollection
            .whereEqualTo("userId", userId)
            .asFlow(transform = { it.sortedByDescending { b -> b.timestamp } }) { doc -> 
                doc.toObject(Booking::class.java)?.copy(id = doc.id) 
            }

    fun getAllBookingsFlow(): Flow<List<Booking>> = 
        bookingsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .asFlow { doc -> doc.toObject(Booking::class.java)?.copy(id = doc.id) }

    fun getBarbersFlow(): Flow<List<Barber>> = 
        barbersCollection.asFlow { doc -> doc.toObject(Barber::class.java)?.copy(id = doc.id) }

    fun getServicesFlow(): Flow<List<BarberService>> = 
        servicesCollection.asFlow { doc -> doc.toObject(BarberService::class.java)?.copy(id = doc.id) }

    fun getPromotionsFlow(): Flow<List<Promotion>> = 
        promosCollection.asFlow { doc -> doc.toObject(Promotion::class.java)?.copy(id = doc.id) }

    suspend fun deleteBooking(booking: Booking): Boolean = try {
        val slotId = "${booking.barberName}_${booking.date}_${booking.time}".replace(" ", "_")
        db.runBatch { batch ->
            batch.delete(bookingsCollection.document(booking.id))
            batch.delete(slotsCollection.document(slotId))
        }.await()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting booking", e)
        false
    }

    suspend fun saveUserProfile(profile: UserProfile): Boolean = try {
        usersCollection.document(profile.uid).set(profile).await()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error saving profile", e)
        false
    }

    suspend fun getUserProfile(uid: String): UserProfile? = try {
        usersCollection.document(uid).get().await().toObject(UserProfile::class.java)
    } catch (e: Exception) {
        Log.e(TAG, "Error getting profile", e)
        null
    }

    suspend fun saveReview(review: Review): Boolean = try {
        val docRef = reviewsCollection.document()
        reviewsCollection.document(docRef.id).set(review.copy(id = docRef.id)).await()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error saving review", e)
        false
    }
}

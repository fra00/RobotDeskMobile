package com.example.mydeskrobot.domain.list

/**
 * Type of structured list item.
 * - NOTE: free-form notes
 * - TODO: tasks with optional checked state
 * - SHOPPING: shopping list items with optional checked state
 */
enum class ListItemType {
    NOTE,
    TODO,
    SHOPPING,
}

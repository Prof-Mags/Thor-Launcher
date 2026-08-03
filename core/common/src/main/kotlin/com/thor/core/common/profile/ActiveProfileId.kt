package com.thor.core.common.profile

import javax.inject.Qualifier

/**
 * The id of the profile currently signed in, as a `Flow<String>`.
 *
 * Declared here rather than beside the registry that produces it so that the
 * library database can follow the active profile without the database module
 * having to know how profiles are stored.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveProfileId

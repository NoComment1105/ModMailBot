package io.github.nocomment1105.modmailbot.extensions.events

import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.checks.noGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.utils.createdAt
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.createTextChannel
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.create.embed
import dev.kord.x.emoji.Emojis
import dev.kord.x.emoji.addReaction
import io.github.nocomment1105.modmailbot.MAIL_SERVER
import io.github.nocomment1105.modmailbot.MAIN_SERVER
import io.github.nocomment1105.modmailbot.database.DatabaseManager
import io.github.nocomment1105.modmailbot.database.getOpenThreadsForUser
import io.github.nocomment1105.modmailbot.messageEmbed
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import mu.KotlinLogging
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class MessageReceiving : Extension() {

	override val name = "messagereceiving"

	override suspend fun setup() {
		val logger = KotlinLogging.logger("Message Receiving")

		event<MessageCreateEvent> {
			check {
				noGuild()
				failIf(event.message.author?.id == kord.selfId || event.message.author == null)
			}
			action {
				var openThread = false
				// Check to see if the user has any threads open already
				newSuspendedTransaction {
					openThread = try {
						DatabaseManager.OpenThreads.select {
							DatabaseManager.OpenThreads.userId eq event.message.author?.id.toString()
						}.single()
						true
					} catch (e: NoSuchElementException) {
						logger.info("No Thread found! Creating thread for ${event.message.author?.id}")
						false
					}
				}

				val mailChannel: TextChannel

				if (!openThread) {
					// Get the mail channel
					mailChannel = kord.getGuild(MAIL_SERVER)!!.createTextChannel(event.message.author!!.tag)

					// Store the users thread in the database
					newSuspendedTransaction {
						DatabaseManager.OpenThreads.insertIgnore {
							it[userId] = event.message.author?.id.toString()
							it[threadId] = mailChannel.id.toString()
						}
					}

					mailChannel.createMessage {
						content = "@here" // TODO Implement a config options system
						// Provide some information about the user in an initial embed
						embed {
							description = "${event.message.author!!.mention} was created " +
									event.message.author!!.fetchUser().createdAt.toDiscord(TimestampType.LongDateTime)
							timestamp = Clock.System.now()
							color = DISCORD_BLURPLE

							field {
								name = "Nickname"
								value = event.message.author!!.asMember(MAIN_SERVER).nickname.toString()
								inline = true
							}

							field {
								val roles = event.message.author!!.asMember(MAIN_SERVER).roles.toList().map { it }
								name = "Roles"
								roles.forEach {
									value += "${it.name}\n"
								}
								inline = true
							}

							author {
								name = event.message.author?.tag
								icon = event.message.author?.avatar!!.url
							}

							footer {
								text = "User ID: ${event.message.author!!.id} |" +
										" DM ID: ${event.message.author!!.getDmChannel().id}"
							}
						}

						// Send the message through to the mail server
						embed {
							messageEmbed(event.message)
						}
					}

					// React to the message in DMs with a white_check_mark, once the message is sent to the mail sever
					event.message.addReaction(Emojis.whiteCheckMark)
				} else {
					val mailChannelId: String

					// Attempt to find threads open in the users id. If this failed something has gone horribly wrong
					try {
						mailChannelId =
							getOpenThreadsForUser(event.message.author!!.id, DatabaseManager.OpenThreads.threadId)
					} catch (e: NoSuchElementException) {
						logger.error("User passed checks yet has no data? What?")
						return@action
					}

					// Get the mail server from the config file
					mailChannel = kord.getGuild(MAIL_SERVER)!!.getChannelOf(Snowflake(mailChannelId))

					// Send the user's message through to the mail server
					mailChannel.createMessage {
						embed {
							messageEmbed(event.message)
						}
					}

					// React to the message in DMs with a white_check_mark, once the message is sent to the mail sever
					event.message.addReaction(Emojis.whiteCheckMark)
				}
			}
		}
	}
}

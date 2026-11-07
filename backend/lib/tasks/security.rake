namespace :security do
  desc "Promote an existing user to operator and set an initial password"
  task bootstrap_operator: :environment do
    email = ENV.fetch("OPERATOR_EMAIL")
    password = ENV.fetch("OPERATOR_INITIAL_PASSWORD")
    user = User.find_by!(email: email)
    user.update!(role: "operator", password: password)
    puts "Operator access configured for user id=#{user.id}; no credentials were printed."
  end

  desc "Set a user's initial or recovered password"
  task set_user_password: :environment do
    email = ENV.fetch("USER_EMAIL")
    password = ENV.fetch("USER_INITIAL_PASSWORD")
    user = User.find_by!(email: email)
    user.update!(password: password)
    Security::AuditLogger.record(event: "user_password_reset", actor: user)
    puts "Password updated for user id=#{user.id}; no credentials were printed."
  end
end

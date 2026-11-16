namespace :dev do
  desc "Credit an account with test money (development/test only): rake dev:fund[ACCOUNT_NUMBER,AMOUNT]"
  task :fund, [ :account_number, :amount ] => :environment do |_task, args|
    abort "dev:fund is disabled outside development/test" unless Rails.env.local?

    account = Account.find_by!(account_number: args[:account_number])
    cents = Pix::Money.parse_cents(args[:amount].to_s)
    account.with_lock { account.update!(balance_cents: account.balance_cents + cents) }
    puts "Account #{account.account_number} balance: #{account.reload.balance}"
  end
end
